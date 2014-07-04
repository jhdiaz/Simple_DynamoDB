Simple_DynamoDB
===============

Implemented a database fully equipped with replication, failure handling, key consistency, and recovery, based on the Amazon Dynamo database. Values were stored based on the key assigned to said value. Storage for a key was determined by hashing a key (SHA-1 hashing was used) and using lexicographical comparison between the key and the hashed id of a node in order to find which partition the key belonged in. Each partition stored data within a SQLite database.
