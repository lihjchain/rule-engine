@echo off
java -jar -server -Xms4096m -Xmx4096m  -Xss1024k -XX:SurvivorRatio=8 -XX:+UseConcMarkSweepGC -Dfile.encoding=UTF-8 rule-engine-monitor-1.0-SNAPSHOT.jar --server.port=8011
@pause
