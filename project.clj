(defproject label-printer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.apache.pdfbox/pdfbox "2.0.8"]
                 [byte-streams "0.2.3"]
                 [com.google.zxing/core "3.3.1"]
                 [com.google.zxing/javase "3.3.1"]]
  :main ^:skip-aot label-printer.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
