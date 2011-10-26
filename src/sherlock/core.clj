(ns sherlock.core
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [clucy.core :as clucy])
  (:import java.util.zip.ZipFile
           java.net.URL))

;;; Fetching Indices

(defn- unzip [source target-dir]
  (let [zip (ZipFile. source)
        entries (enumeration-seq (.entries zip))
        target-file #(io/file target-dir (.getName %))]
    (doseq [entry entries :when (not (.isDirectory entry))
            :let [f (target-file entry)]]
      (.mkdirs (.getParentFile f))
      (io/copy (.getInputStream zip entry) f))))

(def ^{:dynamic true} *base-index-location*
  (io/file (System/getProperty "user.home") ".sherlock"))

(defn index-location [url]
  (io/file *base-index-location* (string/replace url #"[:/]" "_")))

(defn remote-index-url [url]
  (URL. (format "%s/.index/nexus-maven-repository-index.zip" url)))

(defn- download-index [url]
  (with-open [stream (.openStream (remote-index-url url))]
    (let [tmp (java.io.File/createTempFile "sherlock" "index")]
      (try (io/copy stream tmp)
           (unzip tmp (index-location url))
           (finally (.delete tmp))))))

(defn download-needed? [url]
  (not (.exists (index-location url))))

(defn ensure-fresh-index [url]
  (try (when (download-needed? url)
         (download-index url))
       true
       (catch java.io.IOException _
         false)))

;;; Searching

(def ^{:dynamic true} *page-size* 25)

(defn search-repository [url query page]
  (when (ensure-fresh-index url)
    (let [location (.getAbsolutePath (index-location url))
          fetch-count (* page *page-size*)
          offset (* (dec page) *page-size*)
          results (clucy/search (clucy/disk-index location)
                                query fetch-count :default-field :a)]
      (with-meta (drop offset results) (meta results)))))

(defn- split-bar [s] (.split s "\\|"))

(defn parse-result [data]
  (let [[group artifact version classifier] (split-bar (:u data))
        group (when (not= group artifact) group)
        identifier [(symbol group artifact) (format "\"%s\"" version)]
        [_ timestamp size source javadoc signature packaging] (split-bar (:i data))]
    {:version version
     :classifier classifier
     :group-id group
     :artifact-id artifact
     :sha1 (:1 data)
     :name (:n data)
     :description (:d data)
     :updated (Long. timestamp)
     :size (Long. size)
     :packaging packaging}))

(defn search
  "Search a maven repository. Takes a URL to the maven repo, the query string, and
   the number of pages of results you'd like to return. This number will be multiplied
   by *page-size* to get the number of results to return. Returns a sequence of maps of
   artifacts. Each map has the following keys: :version, :artifact-id, :group-id,
   :classifier, :sha1, :name, :description, :updated, :size, and :packaging. Metadata
   on the sequence includes :_total-hits and :_max-score keys.

   Sherlock looks for indexes in ~/.sherlock by default. This can be changed by setting
   *base-index-location*. If the index for the repository that you're searching is not
   found, Sherlock will download the index and place it in *base-index-location*. This
   can take a while, as some indexes are very large."
  [url query pages]
  (let [results (search-repository url query pages)]
    (with-meta (map parse-result results) (meta results))))

(defn update-index
  "Redownload the index for a given repository."
  [url]
  (doseq [f (-> url index-location file-seq reverse)]
    (io/delete-file f :silent))
  (ensure-fresh-index url))