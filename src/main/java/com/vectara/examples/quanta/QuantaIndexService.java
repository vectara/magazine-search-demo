package com.vectara.examples.quanta;

import com.vectara.IndexServiceGrpc;
import com.vectara.ServiceProtos;
import com.vectara.examples.quanta.util.JwtFetcher;
import com.vectara.examples.quanta.util.StatusOr;
import com.vectara.examples.quanta.util.TokenResponse;
import com.vectara.examples.quanta.util.VectaraArgs;
import com.vectara.examples.quanta.util.VectaraCallCredentials;
import com.vectara.indexing.IndexingProtos;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;

public class QuantaIndexService {
  final IndexServiceGrpc.IndexServiceBlockingStub vectaraIndexClient;
  final JwtFetcher jwtFetcher;
  final VectaraArgs args;

  public QuantaIndexService(VectaraArgs args) throws IOException {
    this.args = args;
    jwtFetcher = new JwtFetcher(
        URI.create(args.auth_auth_url),
        args.auth_app_id,
        args.auth_app_secret
    );
    ManagedChannel indexingChannel = NettyChannelBuilder
        .forAddress("indexing.vectara.io", 443)
        .sslContext(GrpcSslContexts.forClient()
            .trustManager((File) null)
            .build())
        .build();
    vectaraIndexClient = IndexServiceGrpc.newBlockingStub(indexingChannel);
  }

  public void index(IndexingProtos.Document indexDoc) throws Exception {
    ServiceProtos.IndexDocumentRequest request = createRequest(
        args.customer_id,
        args.corpus_id,
        indexDoc
    );
    VectaraCallCredentials credentials = getCredentials();
    ServiceProtos.IndexDocumentResponse response = vectaraIndexClient
        .withCallCredentials(credentials)
        .index(request);
    System.out.println(indexDoc.getDocumentId() + " indexed: " + response.getStatus().getCode());
  }

  private VectaraCallCredentials getCredentials() {
    final StatusOr<TokenResponse> fetch = jwtFetcher.fetchClientCredentials();
    return new VectaraCallCredentials(
        VectaraCallCredentials.AuthType.OAUTH_TOKEN,
        fetch.get().getAccessToken().getToken(),
        args.customer_id
    );
  }

  private static ServiceProtos.IndexDocumentRequest createRequest(long customerId, long corpusId, IndexingProtos.Document doc) {
    return ServiceProtos.IndexDocumentRequest
        .newBuilder()
        .setCorpusId(corpusId)
        .setCustomerId(customerId)
        .setDocument(doc)
        .build();
  }
}
