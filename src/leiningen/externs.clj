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
  (binding [*data-readers* (conj *data-readers* {'js identity})]
    (let [stream (java.io.PushbackReader.
                  (io/reader f))
          forms (take-while #(not= ::stream-end %)
                            (repeatedly (fn []
                                          (try (read stream false ::stream-end)
                                               (catch RuntimeException _)))))
          extern-defs (atom [])]
      (walk/postwalk #(find-extern % extern-defs)
                     forms)
      (subtree-postwalk #(find-extern-in-dot-form % extern-defs)
                        forms)
      @extern-defs)))

(defn get-source-paths [build-type builds]
  (or
   (when build-type
     (:source-paths
      (or ((keyword build-type) builds)
          (first (filter #(= (name (:id %)) build-type) builds)))))
   ["src" "cljs"]))

(defn externs
  "Generate an externs file"
  [project & [build-type outfile]]
  (let [source-paths (->> project
                          :cljsbuild
                          :builds
                          (get-source-paths build-type))
        files        (->> source-paths
                          (map #(str (:root project) "/" %))
                          (map io/file)
                          (mapcat file-seq)
                          (filter cljs-file?))
        extern-defs  (->> (mapcat extract-externs files)
                          (mapcat #(s/split % #"\."))
                          (remove empty?)
                          distinct
                          (sort-by (juxt s/upper-case identity)))
        result       (generate-extern-object extern-defs)]
    (if outfile
      (spit outfile result)
      (println result))))
