package org.hac.lambda;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.hac.aws.s3util.S3ImageCompress;
import org.hac.util.AppUtil;
import org.jboss.logging.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("lambdahandler")
public class S3EventHandlerLambda implements RequestHandler<S3Event, Void> {

   final Logger LOGGER = Logger.getLogger("S3EventHandlerLambda");
   AtomicInteger count = new AtomicInteger(0);

    @Inject
    S3ImageCompress s3imgComp;

    @Inject
    AppUtil appUtil;

    

    @Override
    public Void handleRequest(S3Event event, Context context) {
        String funcName = context.getFunctionName();
        appUtil.readConfigData(); // to make sure SSM config data is loaded latest

        int recordCount = event.getRecords().size();
        event.getRecords().forEach(record -> {
            String bucketName = record.getS3().getBucket().getName();
            String objectKey = record.getS3().getObject().getKey();
            String decodedFilename =null;
            try{    
                LOGGER.info("bucket name:"+bucketName+" object key:"+objectKey);
                 decodedFilename = URLDecoder.decode(objectKey, StandardCharsets.UTF_8.toString());
            }catch(Exception e){
                LOGGER.error("Error decoding filename:"+objectKey);
                return;
            }
            boolean isValidImg = appUtil.validate(decodedFilename);
            if(isValidImg){
                s3imgComp.compressS3File(bucketName,decodedFilename);
                int currentCount = count.incrementAndGet();
            }else{
                LOGGER.info("Invalid file ext for image compression skipping lambda for :"+objectKey);
            }
        });
        return null;
    }

    void onStart(@Observes StartupEvent ev) {               
        LOGGER.info("The application is starting...");
    }

    void onStop(@Observes ShutdownEvent ev) {               
        LOGGER.info("The application is stopping Reuests processed by this instance:"+count.get());
    }



}