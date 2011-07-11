(ns sqleton.connection
  (:use cake
        [bake.core :only [in-cake-jvm?]]
        [clojure.contrib.string :only [join split]]
        [clojure.contrib.sql :only [with-connection]]))

(def *datasource* nil)

(defn load-user-config [source-name]
  (let [overrides (java.io.File. (str (System/getProperty "user.home") "/.sqleton/" (:artifact-id *project*) ".clj"))]
    (when (.exists overrides)
      (get (read-string (slurp overrides)) source-name))))

(defn system-overrides [source-name]
  (into {} (for [key [:host :port :database :user :password]
                 :let [value (System/getProperty (join "." (map name ["sqleton" source-name key])))]
                 :when value]
             [key value])))

(defn datasource
  "Returns a map appropriate for either a jndi or traditional data source."
  [source-name]
  (with-meta
    (if (or (in-cake-jvm?) (nil? (get-in *project* [:db source-name :name])))
      (let [defaults (get-in *project* [:db source-name])
            config   (merge defaults (load-user-config source-name) (system-overrides source-name))
            {:keys [host port database user password]} config]
        {:classname   "org.postgresql.Driver"
         :subprotocol "postgresql"
         :user        (or (System/getProperty "sqleton") user (System/getProperty "user.name"))
         :password    password
         :subname     (str "//" host ":" (or port 5432) "/" database)})
      (get-in *project* [:db source-name]))
    {:name source-name}))

(defmacro with-db
  "Create a connection to a specific db if there is not one for this thread already."
  [source-name & forms]
  `(if (= ~source-name (:name (meta *datasource*)))
     (do ~@forms)
     (binding [*datasource* (datasource ~source-name)]
       (with-connection *datasource*
         ~@forms))))