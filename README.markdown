# Sherlock

Sherlock is a library for downloading and searching lucene indexes of maven repositories. It is pulled from [leiningen](http://github.com/technomancy/leiningen) and generalized.

## Usage

```clojure
user> (require '[sherlock.core :as sherlock])
nil
user> (search "http://clojars.org/repo" "hobbit" 1)
({:group-id nil, :name "hobbit", :version "0.1.0-SNAPSHOT", :size 7733, :packaging "jar", :updated 1319091283000, :sha1 "6b9d982e8dea925034c5cbe080a767c36f677804", :classifier "NA", :artifact-id "hobbit", :description "A library for interfacing with various URL shortening services."})
```

For more information, `(doc sherlock/search)`.
