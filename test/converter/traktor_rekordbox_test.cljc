(ns converter.traktor-rekordbox-test
  (:require
   #?(:clj [clojure.spec.alpha :as s] :cljs [cljs.spec.alpha :as s])
   #?(:clj [clojure.spec.gen.alpha :as gen] :cljs [cljs.spec.gen.alpha :as gen])
   [clojure.test :refer [is] #?@(:cljs [:include-macros true])]
   [clojure.test.check.clojure-test :refer [defspec] #?@(:cljs [:include-macros true])]
   [clojure.test.check.properties :as tcp #?@(:cljs [:include-macros true])]
   [converter.test-utils :as test]
   [converter.universal.core :as u]
   [plumula.mimolette.alpha :refer [defspec-test]]))

(defspec traktor-rekordbox-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)]
               (as-> library $
                 (test/traktor-round-trip $)
                 (test/rekordbox-round-trip $)
                 (is (= ((comp test/library-equiv-rekordbox test/library-equiv-traktor) library)
                        ((comp test/library-equiv-rekordbox test/library-equiv-traktor) $))))))

(defspec rekordbox-traktor-round-trip-library-equality
  10
  (tcp/for-all [library (s/gen u/library-spec)]
               (as-> library $
                 (test/rekordbox-round-trip $)
                 (test/traktor-round-trip $)
                 (is (= ((comp test/library-equiv-traktor test/library-equiv-rekordbox) library)
                        ((comp test/library-equiv-traktor test/library-equiv-rekordbox) $))))))