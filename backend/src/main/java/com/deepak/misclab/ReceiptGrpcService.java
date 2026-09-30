package com.deepak.misclab;

import com.deepak.misclab.grpc.GetReceiptRequest;
import com.deepak.misclab.grpc.ReceiptGrpcGrpc;
import com.deepak.misclab.grpc.ReceiptReply;
import io.grpc.stub.StreamObserver;

/** gRPC adapter over the same ReceiptService as REST/GraphQL. Generated base comes from receipts.proto. */
public class ReceiptGrpcService extends ReceiptGrpcGrpc.ReceiptGrpcImplBase {
    private final ReceiptService service;

    public ReceiptGrpcService(ReceiptService service) {
        this.service = service;
    }

    @Override
    public void getReceipt(GetReceiptRequest request, StreamObserver<ReceiptReply> responseObserver) {
        Receipt r = service.get(request.getId());
        ReceiptReply.Builder b = ReceiptReply.newBuilder().setFound(r != null);
        if (r != null) {
            b.setReceiptId(r.receiptId())
                    .setPayer(r.payer())
                    .setAmount(r.amount().toPlainString())
                    .setCurrency(r.currency())
                    .setUsdAmount(r.usdAmount().toPlainString());
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }
}
