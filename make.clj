#!/usr/bin/env bb
(ns magazinepdf.main
  (:require [clojure.java.io]
            [clojure.string]
            [clojure.java.shell :refer [sh]]))

(defn extension
  [f]
  (let [base (.getName f)
        dot (.lastIndexOf base ".")]
    (when (pos? dot)
      (subs base dot))))

(defn trim-ext [f]
  (let [base (.getName f)
        dot (.lastIndexOf base ".")]
    (if (pos? dot) (subs base 0 dot) 
        base)))

(defn pages
  [root]
  (let [dir (file-seq (clojure.java.io/file root))
        jpegs (filter
               (fn [f] (and (.isFile f) (= (extension f) ".jpg")))
               dir)
        pages (map (fn [f]
                     (let [page {:jpeg (str f)
                                 :id (trim-ext f)}
                           png (clojure.java.io/file
                                (str "png/" (trim-ext f) ".png"))]
                       (if (.exists png)
                         (assoc page :png (str png))
                         page)))
                   jpegs)]

    (sort-by :jpeg pages)))

(defn wrap-sh [& args]
  (println args)
  (let [{exit :exit, out :out, err :err} (apply sh args)]
    (if (zero? exit)
      out
      (throw (Exception. err)))))

(defn image-size [path]
  (let [out (wrap-sh "magick" "identify" path)]
      (nth (clojure.string/split out #"\s+") 2)))

(defn resize-png [path size]
  (let [outpath (str "tmp/resized_" (.getName (clojure.java.io/file path)))]
    (wrap-sh "magick" path "-resize" size outpath)
    outpath))

(defn compose [path1 path2 path-out]
  (wrap-sh "magick" "composite"
           "-compose" "atop"
           "-colorspace" "sRGB"
           path1 path2 path-out)
  path-out)

(defn process-page [page size]
  (if-let [png (:png page)]
    (let [resized-png (resize-png png size)
          result-path (str "tmp/composed_" (:id page) ".jpg")]
      (compose resized-png (:jpeg page) result-path)
      result-path)
    (:jpeg page)))


(defn -main []
  (.mkdir (clojure.java.io/file "tmp"))

  (let [pages (pages "jpg")
        size (image-size (-> pages first :jpeg))
        _ (println "Size" size)
        page-paths (mapv #(process-page % size) pages)]

    (apply wrap-sh "magick" "convert" (conj page-paths "result.pdf"))
    
    
    ))

(-main)
