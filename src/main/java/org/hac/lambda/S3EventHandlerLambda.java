package org.hac.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.hac.aws.s3util.S3ImageCompress;

import java.util.concurrent.atomic.AtomicInteger;

@Named("lambdahandler")
public class S3EventHandlerLambda implements RequestHandler<S3Event, Void> {
    @Inject
    S3ImageCompress s3imgComp;

    @Override
    public Void handleRequest(S3Event event, Context context) {
        AtomicInteger count = new AtomicInteger(0);
        event.getRecords().forEach(record -> {
            String bucketName = record.getS3().getBucket().getName();
            String objectKey = record.getS3().getObject().getKey();
            s3imgComp.compressS3File(bucketName,objectKey);
            int currentCount = count.incrementAndGet();
        });
        return null;
    }
}