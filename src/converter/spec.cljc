(ns converter.spec
  (:require
   [cemerick.url :as url]
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.string :as str]
   [clojure.zip :as zip]
   [converter.map :as map]
   [spec-tools.core :as st]
   [spec-tools.transform :as stt]))

(s/def ::not-blank
  (s/and string? #(not (str/blank? %))))

(def drive-letter-regex #"[A-Z]:")

(defn drive-letter?
  [str]
  (if (string? str)
    (boolean (re-matches drive-letter-regex str))
    false))

(s/def ::drive-letter
  (s/with-gen
    (s/and string? drive-letter?)
    (fn [] (gen/fmap #(-> % str/upper-case (str ":")) (gen/char-alpha)))))

(defn string->url
  [_ str]
  (if (string? str)
    (try
      (url/url str)
      (catch #?(:clj Exception, :cljs js/Error) _ str))
    str))

; TODO proper generator, rename to indicate file urls?
(s/def ::url (st/spec #(instance? cemerick.url.URL %) {:type :url
                                                       :gen #(s/gen #{(url/url "file://localhost/foo/bar")})}))

(s/def ::pos-double
  (st/spec (s/and double? pos?) {:gen (fn [] (->> (gen/double)
                                                  (gen/such-that #(and (not= 0 %) (== % %)))   ; (not (== % %)) ##NaN ##Inf check
                                                  (gen/fmap #(Math/abs %))))}))

; s/with-gen doesn't work with spec tools coercion in cljs, so I use st/spec and :gen
(s/def ::not-neg-double
  (st/spec (s/and double? #(not (neg? %))) {:gen (fn [] (->> (gen/double)
                                                             (gen/such-that #(== % %))   ; (not (== % %)) ##NaN ##Inf check
                                                             (gen/fmap #(Math/abs %))))}))

; TODO can't be implemented yet, if encoded value is invalid, 
; there's no way to coerce using encoder rather than decoder
; (defn encode!
;   [spec value transformer])

(defn decode!
  [spec value transformer]
  ; st/conform + s/unform is used instead of st/decode, because currently st/coerce 
  ; (and therefore also st/decode) can't coerce for specs that use s/cat and regex ops
  ; https://github.com/metosin/spec-tools/issues/149
  (let [conformed (st/conform spec value transformer)]
    (if-not (s/invalid? conformed)
      (s/unform spec conformed)
      (let [explain (st/explain-data spec value transformer)
            data {:type ::decode
                  :problems (st/+problems+ explain)
                  :spec spec
                  :value value}]
        (throw (ex-info "Spec decode error:" data))))))

(def xml-transformer
  (st/type-transformer
   {:name :xml
    :decoders (merge stt/string-type-decoders {:url string->url})
    :encoders (merge stt/string-type-encoders {:url stt/any->string})
    :default-encoder stt/any->any}))

(def string-transformer
  (st/type-transformer
   {:name :string
    :decoders (merge stt/string-type-decoders {:url string->url})
    :encoders (merge stt/string-type-encoders {:url stt/any->string})
    :default-encoder stt/any->any}))

; this only works as long as it's not nested inside other data specs
(defrecord XmlZipSpec [spec]
  s/Spec
  (conform* [_ v]
    (s/conform spec (zip/node v)))
  (unform* [_ v]
    (s/unform spec (zip/node v)))
  (explain* [_ path via in v]
    (s/explain* spec path via in (zip/node v)))
  (gen* [_ overrides path rmap]
    (gen/fmap #(zip/xml-zip %) (s/gen* spec overrides path rmap))))

(defn xml-zip-spec [spec]
  (->XmlZipSpec spec))

; TODO spec is assumed to be a spec-tools record
(defn remove-empty-spec
  [spec & ks]
  (assoc spec :gen (fn [] (gen/fmap #(apply map/remove-empty % ks) (s/gen spec)))))

; TODO spec is assumed to be a spec-tools record
(defn such-that-spec
  [spec pred max-tries]
  (assoc spec :gen (fn [] (gen/such-that pred (s/gen spec) max-tries))))

(defrecord ValueEncodedSpec [spec transformer]
  s/Spec
  (conform* [_ v]
    (st/conform spec v transformer))
  (unform* [_ v]
    (s/unform spec v))
  (explain* [_ path via in v]
    (prn "[ValueEncodedSpec/explain*] path: " path)
    (prn "[ValueEncodedSpec/explain*] via: " via)
    (prn "[ValueEncodedSpec/explain*] in: " in)
            ; TODO this isn't quite right, path, via and in are ignored.. need to duplicate spec-tools.core/Spec/explain*
    (st/explain spec v transformer))
  (gen* [_ overrides path rmap]
    (gen/fmap #(st/encode spec % transformer) (s/gen* spec overrides path rmap))))

(defn value-encoded-spec
  [spec transformer]
  (->ValueEncodedSpec spec transformer))
