(defproject revdiff "1.0"
  :dependencies [[org.clojure/clojure "1.6.0"] [org.clojure/tools.cli "0.3.1"]]
  :description "A Subversion history search / diff tool"
  :license {:name "Apache License Version 2.0" :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :main ^:skip-aot revdiff.core
  :profiles {:uberjar {:aot :all}}
  :target-path "target/%s"
  :uberjar-name "revdiff.jar"
  :url "https://github.com/maddenp/revdiff")
