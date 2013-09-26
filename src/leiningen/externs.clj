(ns leiningen.externs
  (:require [clojure.string  :as s]
            [clojure.walk    :as walk]
            [clojure.java.io :as io]
            [clojure.pprint  :refer [pprint]]))

(defn cljs-file?
  "Returns true if the java.io.File represents a normal Clojurescript source
file."
  [^java.io.File file]
  (and (.isFile file)
       (.endsWith (.getName file) ".cljs")))

(defn strip [n]
  (s/replace n #"^\.-|^\.|^-|\.$" ""))

(defn subtree-postwalk [f coll]
  (walk/walk
   #(do (f %)
        (subtree-postwalk f %))
   (constantly nil)
   coll))

(defn generate-extern-object [defs]
  (let [fns (map #(str "\"" % "\" : function () {}" ) defs)
        fn-defs (apply str (interpose ",\n" fns))]
    (str "var TopLevel = {\n" fn-defs "\n}")))

(defn prop? [n]
  (or (.startsWith n ".")
      (.endsWith n ".")))

(defn js-form? [x]
  (= (namespace x) "js"))

(defn find-extern [x extern-defs]
  (when (symbol? x)
    (let [n (name x)]
      (cond
       (js-form? x) (swap! extern-defs into (s/split n #"\."))
       (prop? n) (swap! extern-defs conj (strip n))))))

(defn get-prop [x]
  (cond
   (symbol? x) x
   (and (seq? x) (symbol? (first x))) (first x)))

(defn find-extern-in-dot-form [x extern-defs]
  (when (and (seq? x) (or (= '. (first x))
                          (= '.. (first x))))

    (->> (if (= '. (first x))
           [(nth x 2)]
           (drop 2 x))
         (map get-prop)
         (map strip)
         (swap! extern-defs into))))

(defn extract-externs [f]
  (let [stream (java.io.PushbackReader.
               (io/reader f))
        forms (take-while #(not= ::stream-end %)
                          (repeatedly (partial read stream false ::stream-end)))
        extern-defs (atom [])]
    (walk/postwalk #(find-extern % extern-defs)
                   forms)
    (subtree-postwalk #(find-extern-in-dot-form % extern-defs)
                      forms)
    @extern-defs))

(defn externs
  "Generate an externs file"
  [project & [build-type]]
  (let [source-paths (if build-type
                       (->> project
                            :cljsbuild
                            :builds
                            ((keyword build-type))
                            :source-paths)
                         ["src" "cljs"])
        files        (->> source-paths
                          (map #(str (:root project) "/" %))
                          (map io/file)
                          (mapcat file-seq)
                          (filter cljs-file?))
        extern-defs  (->> (mapcat extract-externs files)
                          (remove empty?)
                          distinct
                          (sort-by s/upper-case))
        result       (generate-extern-object extern-defs)]
    (println result)))
