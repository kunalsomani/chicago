echo 'Runn:ing Chicago'
java  -ea                         \
  $JAVA_OPTS                      \
  -Djava.net.preferIPv4Stack=true \
  -XX:+UseStringDeduplication     \
  -XX:+UseTLAB                    \
  -XX:+AggressiveOpts             \
  -XX:+UseParNewGC                \
  -XX:+UseConcMarkSweepGC         \
  -XX:+CMSParallelRemarkEnabled   \
  -XX:+CMSClassUnloadingEnabled   \
  -XX:ReservedCodeCacheSize=128m  \
  -XX:SurvivorRatio=128           \
  -XX:MaxTenuringThreshold=0      \
  -Xss8M                          \
  -Xms1024M                       \
  -Xmx12G                         \
  -server                         \
  -cp ../target/chiacgo-0.1.0-SNAPSHOT.jar com.xjeffrose.chicago.Chiacgo "$@"