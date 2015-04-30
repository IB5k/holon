(ns holon.datomic.protocols)

(defprotocol DatomicDatabase
  (url [_] "database url"))

(defprotocol DatomicNorms
  (norms [_] "{keys {:txes []}"))

(defprotocol ListenDatomicReportQueue
  (tap-tx-queue! [_] "return a channel of all tx reports"))

(defprotocol DatomicTXListener
  (tx-handler [_] "returns a fn that is called in response to query novelty"))
