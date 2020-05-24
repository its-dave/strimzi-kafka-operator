This temporary file is a record of the delta between this fork repo, and the upstream github.com/strimzi/strimzi-kafka-operator

### 23-May-2020

`master-ibm` has been rebased with commit `3418771a3e6137fdbff793d3061c9a494633e007`

This is the last commit before the 0.18.0 release branch was started.

https://github.com/strimzi/strimzi-kafka-operator/commit/3418771a3e6137fdbff793d3061c9a494633e007

The following commits were then cherry-picked on top.

(These are all the upstream master branch version of commits that are in the 0.18.0 release.)

```
2745af555da767dced193e6f02afb2787534662b Added Cruise Control, resource and operator to the changelog (#3040)
7a3b6d84fa316ad597dc02db667b851e0b078d52 Fix typo in the Bridge version in the CHANGELOG (#3038)
f9715e58ec5f26dce117d141575feb2107dddd93 Fix parsing of forbidden configuration options and their exceptions (#3046)
aa6df9b882bfb7f6097a444e8045d250cc3fec86 Add the KafkaRebalance CR to the strimzi category (#3045)
ebcaaed697fb593296c7758530fd8bc264f3a559 Fix rebalance examples (#3050)
80398b211aa887b316de0f42f3a10f3cf915ab94 Added KafkaRebalance counter to the Grafana operators dashboard (#3052)
19b694f31184671ea62faa3084f6eba591db61c0 Fix Prometheus operator installation (#3049)
7c4f5c87fcb5440f6f9ecdc24f8f36f14ce5c24b [DOC] Added config section for KafkaConnector custom resource (#3051)
6b0f1c73486a3501201352805239cca5fd809342 [DOC] Add note on replicas for changing topics (#3030)
831f3436e91d054d559cae1ed37d1acd56eefd5f ST: Fix test, which check that Kafka roll (or not) when topic block it (#3041)
183d3ce6d1b4661cc30797fdf5939d0e2552b003 [DOC] Cruise Control: rebalance procedures and overview of goals and proposals (#2956)
3ff82862297dee81db504327e97a69f6093b1291 Fix incorrect warning about loadBalancerSourceRanges field (#3055)
```

I also cherry-picked the following commits, not included in the upstream 0.18.0 release

```
e80084547383f162cf47f52e2bdf9678af8732e3 Moved Dockerfile copies of Cruise Control in the right position (#3070)
26bf33dab8cda98d998680e7b698808433078071 Added tini for running components where missing (#3079)
```


### 24-May-2020

I cherry-picked an additional commit, with a fix we need for geo-replication

```
c93ff4624e386e37f2dcbc08a08d56a622b2bef7 fix: Ensure multiple MM2 spec.clusters can use the same secret (#3082)
```
