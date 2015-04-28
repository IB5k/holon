(ns holon.events.protocols)

(defprotocol EventProducer
  (event-schema [_] "a map of event keys and args"))

(defprotocol EventHandler
  (event-handlers [_] "a collection of functions or maps that return event handlers"))
