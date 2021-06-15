-include= ~${workspace}/cnf/resources/bnd/feature.props
symbolicName=io.openliberty.internal.grpcClient-1.0
visibility=private
singleton=true
IBM-API-Package: \
  io.grpc.netty; type="third-party", \
  io.netty.handler.ssl; type="third-party", \
  io.openliberty.grpc.internal.client; type="internal"
Subsystem-Version: 1.0.0
Subsystem-Name: gRPC Client 1.0
-features=com.ibm.websphere.appserver.internal.slf4j-1.7.7, \
  io.openliberty.internal.grpc-1.0
-bundles=\
  io.openliberty.grpc.1.0.internal.client, \
  io.openliberty.grpc.client.1.0.thirdparty; location:="dev/api/third-party/,lib/", \
  io.openliberty.org.apache.commons.logging
kind=ga
edition=core
WLP-Activation-Type: parallel
