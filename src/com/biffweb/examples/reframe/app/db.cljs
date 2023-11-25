(ns com.biffweb.examples.reframe.app.db)

(def default-db
  {:loading      true
   :current-user {}
   :messages     []
   :csrf-token   nil
   :socket       nil})
