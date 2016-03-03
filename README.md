# lein-externs

A Leiningen plugin to generate externs for your ClojureScript project

## Usage

Put 
[![Clojars Project](https://img.shields.io/clojars/v/lein-externs.svg)](https://clojars.org/lein-externs)
 into your `:user` profile or the `:plugins` vector of your project.clj file

Then run it with

```
lein externs [build] [output file]
``` 

Without the output file the extern is printed to standard out.


### Example:

To use the default build for the project, run:
```
lein externs > externs.js
```
or
```
lein externs nil externs.js
```


Specify the release build with:

```
lein externs release externs.js
```


## License

Copyright © 2013-2015 Rasmus Buchmann, Erik Ouchterlony, Benjamin Teuber

Distributed under the Eclipse Public License, the same as Clojure.
