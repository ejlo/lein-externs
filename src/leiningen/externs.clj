(ns leiningen.externs
  (:require [clojure.string :as s]
            [clojure.pprint :refer [pprint]]))


(defn cljs-file?
  "Returns true if the java.io.File represents a normal Clojurescript source
file."
  [^java.io.File file]
  (and (.isFile file)
       (.endsWith (.getName file) ".cljs")))

(defn strip [n]
  (s/replace n #"^\.-|^\.|\.$" ""))

(defn generate-extern-object [defs]
  (let [fns (map #(str "\"" % "\" : function () {}" ) defs)
        fn-defs (apply str (interpose ",\n" fns))]
    (str "var TopLevel = {\n" fn-defs "\n}")))

(defn extract-externs [f]
  (let [stream (java.io.PushbackReader.
               (clojure.java.io/reader f))
        forms (take-while #(not= ::stream-end %)
                          (repeatedly (partial read stream false ::stream-end)))
        extern-defs (atom [])]
    (clojure.walk/postwalk (fn [x]
                             (when (symbol? x)
                                 (let [n (name x)]
                                   (when (or (.startsWith n ".")
                                             (.endsWith n "."))
                                     (swap! extern-defs conj (strip n)))))
                             x)
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
