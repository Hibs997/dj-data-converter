(ns converter.test-utils
  (:require
   [converter.rekordbox.core :as r]
   [converter.spec :as spec]
   [converter.traktor.core :as t]
   [converter.universal.core :as u]
   [converter.universal.marker :as um]
   [converter.universal.tempo :as ut]
   [converter.xml :as xml]
   [spec-tools.core :as st]))

(defn- item-tempos-dissoc-bpm-metro-battito
  [item]
  (if (::u/tempos item)
    (update item ::u/tempos (fn [tempos] (mapv #(dissoc % ::ut/bpm ::ut/metro ::ut/battito) tempos)))
    item))

(defn- library-items-tempos-dissoc-bpm-metro-battito
  [library]
  (if (::u/collection library)
    (update
     library
     ::u/collection
     (fn [items] (map #(item-tempos-dissoc-bpm-metro-battito %) items)))
    library))

(defn library-equiv-traktor
  "Returns a library that is expected to be equivalent with the given library, after it has been converted to Traktor data and back again"
  [library]
  ; TODO for the first tempo of each item, assert bpm's are equal (in addition to inizio being equal)
  (library-items-tempos-dissoc-bpm-metro-battito library))

(defn- library-items-filter-contains-total-time
  [library]
  (if (::u/collection library)
    (update
     library
     ::u/collection
     (partial filter u/item-contains-total-time?))
    library))

(defn- marker-type-supported?
  [marker-type]
  (contains? #{::um/type-cue ::um/type-loop} marker-type))

(defn- item-markers-unsupported-type->cue-type
  [item]
  (if (::u/markers item)
    (update item ::u/markers (fn [markers] (mapv #(if (marker-type-supported? %)
                                                    %
                                                    (assoc % ::um/type ::um/type-cue)) markers)))
    item))

(defn- library-items-markers-unsupported-type->cue-type
  [library]
  (if (::u/collection library)
    (update
     library
     ::u/collection
     (fn [items] (map #(item-markers-unsupported-type->cue-type %) items)))
    library))

(defn library-equiv-rekordbox
  "Returns a library that is expected to be equivalent with the given library, after it has been converted to Rekordbox data and back again"
  [library]
  (-> library
      library-items-filter-contains-total-time
      library-items-markers-unsupported-type->cue-type))

(defn traktor-round-trip
  [library]
  (as-> library $
    (st/encode (t/nml-spec) $ spec/xml-transformer)
    (xml/encode $)
    (xml/decode $)
    (spec/decode! (t/nml-spec) $ spec/string-transformer)
    (spec/decode! t/library-spec $ spec/xml-transformer)))

(defn rekordbox-round-trip
  [library]
  (as-> library $
    (st/encode (r/dj-playlists-spec) $ spec/xml-transformer)
    (xml/encode $)
    (xml/decode $)
    (spec/decode! (r/dj-playlists-spec) $ spec/string-transformer)
    (spec/decode! r/library-spec $ spec/xml-transformer)))
