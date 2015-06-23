(ns holon.datomic.protocols)

(defprotocol DatomicDatabase
  (url [_] "database url"))

(defprotocol DatomicNorms
  (norms [_] "{keys {:txes []}"))

(defprotocol DatomicReportStream
  (tx-stream [_] "return a stream of all tx reports"))

(defprotocol DatomicTXListener
  (tx-handler [_] "returns a fn that is called in response to query novelty"))
