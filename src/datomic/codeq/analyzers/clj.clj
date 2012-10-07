;;   Copyright (c) Metadata Partners, LLC. All rights reserved.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns datomic.codeq.analyzers.clj
  (:require [datomic.api :as d]
            [datomic.codeq.util :refer [cond-> index->id-fn tempid?]]
            [datomic.codeq.analyzer :as az]))

(defn analyze-1
  "returns [tx-data ctx]"
  [db f x loc seg ret {:keys [sha->id added ns] :as ctx}]
  (if loc
    (let [sha (-> seg az/ws-minify az/sha)
          cid (sha->id sha)
          newcid (and (tempid? cid) (not (added cid)))
          ret (cond-> ret newcid (conj {:db/id cid :code/sha sha :code/text seg}))
          added (cond-> added newcid (conj cid))]
      [ret (assoc ctx :added added)])
    [ret ctx]))

(defn analyze 
   [a db f src]
   (with-open [r (clojure.lang.LineNumberingPushbackReader. (java.io.StringReader. src))]
     (let [loffs (az/line-offsets src)
           eof (Object.)
           ctx {:sha->id (index->id-fn db :code/sha)
                :codename->id (index->id-fn db :code/name)
                :added #{}}]
       (loop [ret [], ctx ctx, x (read r false eof)]
         (if (= eof x)
           ret
           (let [{:keys [line column]} (meta x)
                 ctx (if (and (coll? x) (= (first x) 'ns))
                       (assoc ctx :ns (second x))
                       ctx)
                 endline (.getLineNumber r)
                 endcol (.getColumnNumber r)
                 [loc seg] (when (and line column)
                             [(str line " " column " " endline " " endcol)
                              (az/segment src loffs (dec line) (dec column) (dec endline) (dec endcol))])
                 [ret ctx] (analyze-1 db f x loc seg ret ctx)]
             (recur ret ctx (read r false eof))))))))

(deftype CljAnalyzer []
  az/Analyzer
  (keyname [a] :clj)
  (revision [a] 1)
  (extensions [a] [".clj"])
  (schemas [a] {1 [{:db/id #db/id[:db.part/db]
                    :db/ident :clj/ns
                    :db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one
                    :db/doc "codename of ns defined by expression"
                    :db.install/_attribute :db.part/db}
                   {:db/id #db/id[:db.part/db]
                    :db/ident :clj/def
                    :db/valueType :db.type/ref
                    :db/cardinality :db.cardinality/one
                    :db/doc "codename defined by expression"
                    :db.install/_attribute :db.part/db}]})
  (analyze [a db f src] (analyze a db f src)))

(defn impl [] (CljAnalyzer.))