package org.hac.aws.s3util;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.hac.util.AppUtil;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import net.coobird.thumbnailator.Thumbnails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.cloudwatchlogs.emf.logger.MetricsLogger;
import software.amazon.cloudwatchlogs.emf.model.DimensionSet;
import software.amazon.cloudwatchlogs.emf.model.Unit;
import software.amazon.lambda.powertools.metrics.Metrics;
import software.amazon.lambda.powertools.metrics.MetricsUtils;


@ApplicationScoped
public class S3ImageCompress {

     MetricsLogger metricsLogger = MetricsUtils.metricsLogger();

    /*
    public static double imgCompressionScale = 0.50;
    public static double outputQuality = 0.2; // 0.0 to 1.0 , e.g 0.9 is 90% quality

    public static String destFolder = "/tmp/compress/";
    public static String SRCFolder = "/tmp/";

    //public static String destBucketName = "admin.beehive.holiday";
    public static String destBucketName = "amrootdata--eun1-az1--x-s3";
*/


    @Inject
    AppUtil appUtil;

    
    
    @PostConstruct
    @Metrics(namespace = "HacImageUtilLambdas", service = "ImageCompress")
    public void initMetrics() {
        metricsLogger.putDimensions(DimensionSet.of("environment", "prod"));
    }
    

    
    public  void compressS3File(String bucketName,String keyName){

        File S3File = getFileFromS3(bucketName, keyName);
        String  compS3File = compressLocalFile(S3File);
        boolean status = updateS3(appUtil.getS3DestBucketName(),compS3File,keyName);
        
        metricsLogger.putMetric("ImgCompressCount", 1, Unit.COUNT);
        metricsLogger.flush();

    }


    private String[] getFileAndPath(String keyName){
        String[] fileAndPath = new String[2];
        String triggerprefix = appUtil.getS3SRCBucketPrefix();
        if(keyName.contains(File.separator)) {
            fileAndPath[0] = keyName.substring(keyName.lastIndexOf(File.separator) + 1);
            if(triggerprefix !=null || triggerprefix.length() >0){
                String pathwithTriggerprifix = keyName.substring(0, keyName.lastIndexOf(File.separator));
                String pathwithoutTriggerprifix = pathwithTriggerprifix.replaceFirst(triggerprefix, "");
                fileAndPath[1] = pathwithoutTriggerprifix;
                System.out.println("pathwithoutTriggerprifix:"+pathwithoutTriggerprifix);
            }else
                fileAndPath[1] = keyName.substring(0, keyName.lastIndexOf(File.separator));
        }else{
            fileAndPath[0] = keyName;
            fileAndPath[1] = "";
        }
        return fileAndPath;
    }

    public  File getFileFromS3(String bucketName,String keyName){
        long startTime = System.currentTimeMillis();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        String[] fileAndPath = getFileAndPath(keyName);
        System.out.println("KeyName:"+keyName);
        // Extract the file name with extension
        String fileNameWithExtension = fileAndPath[0];
        String pathBeforeFileName = fileAndPath[1];
 

        // Extract the path before the file name
        File baseTempDir = new File(appUtil.getLocalSRCFolder()+pathBeforeFileName+"/");
        
        System.out.println("Directories Created :"+baseTempDir.getAbsolutePath()+" >:"+baseTempDir.mkdirs());

        File myFile = new File(appUtil.getLocalSRCFolder()+fileNameWithExtension);

        try{
            ResponseBytes<GetObjectResponse> objectBytes = appUtil.s3client.getObjectAsBytes(getObjectRequest);
            byte[] data = objectBytes.asByteArray();

            // Write the data to a local file.
            OutputStream os = new FileOutputStream(myFile);
            os.write(data);
            System.out.println("Successfully obtained bytes from an S3 object");
            os.close();
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
        System.out.println("getFileFromS3 took: "+TimeUnit.MILLISECONDS.toMillis(System.currentTimeMillis() - startTime));
        return myFile;
    }

    public  String compressLocalFile(File s3File){
        long startTime = System.currentTimeMillis();

        File destinationDir = new File(appUtil.getLocalCompressFolder());
        destinationDir.mkdirs();

        String destFile = destinationDir + "/" + s3File.getName();
        System.out.println("Dest Files created :"+destFile);
    /*    if(s3File.getName().endsWith("png")){
            PngImage inputImage = null;
            try {
                inputImage = new PngImage(Files.newInputStream(Path.of(s3File.getAbsolutePath())));
                PngOptimizer optimizer = new PngOptimizer();
                PngImage optimized = optimizer.optimize(inputImage);
                OutputStream output = Files.newOutputStream(Paths.get(destFile));
                optimized.writeDataOutputStream(output);
                System.out.println("Successfully handled PNG image file");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }else{ */
            try {
                BufferedImage originalImage = ImageIO.read(s3File);
                Thumbnails.of(originalImage)
                        .scale(appUtil.getCompressionScale())
                        .outputQuality(appUtil.getImgQuality())
                        .toFile(destFile);
                System.out.println("Compressed img file :"+destFile);
            } catch (IOException ioe) {
                System.out.println(ioe.getMessage()); 
            }
      //  } if for png

      double spaceDifferenceMB = spaceDifferenceMB( new File(destFile).length(),s3File.length());
       metricsLogger.putMetric("DiskSpaceSaved", spaceDifferenceMB, Unit.MEGABYTES);
        metricsLogger.flush();
      System.out.println("compressLocalFile took: "+TimeUnit.MILLISECONDS.toMillis(System.currentTimeMillis() - startTime)+ "Mills");
        return destFile;
    }

    private  boolean updateS3(String bucketName, String compS3File,String keyName) {
        long startTime = System.currentTimeMillis();
        System.out.println("updating S3 location :"+compS3File);
        File ogFile = new File(compS3File);
           String[] fileAndPath = getFileAndPath(keyName);
        // Extract the file name with extension
        String pathBeforeFileName = fileAndPath[1];
        String filePathwithOGPrefix =  appUtil.getDestBucketPrefix()+ pathBeforeFileName +File.separator+ ogFile.getName();
        System.out.println("DestFilewithOGFileprefix:"+filePathwithOGPrefix);

        PutObjectRequest putRequest =  PutObjectRequest.builder()
                .bucket(bucketName)
                .key(filePathwithOGPrefix)
                .build();

        PutObjectResponse putResponse = appUtil.s3client.putObject(putRequest, Paths.get(compS3File));
        System.out.println("eTag : "+putResponse.eTag());
        System.out.println("updateS3 took: "+TimeUnit.MILLISECONDS.toMillis(System.currentTimeMillis() - startTime));
        if (putResponse == null) return false;
        else {
            System.out.println("OG File Deleted:"+ ogFile.delete() + "Compressed File Delete:"+ new File(compS3File).delete());
            return true;
        }
    }

    private  double spaceDifferenceMB(long size1, long size2) {
        // Ensure non-zero denominator to avoid division by zero
        if (size1 == 0) {
            return Double.POSITIVE_INFINITY;
        }
         // Calculate the difference in disk space in megabytes (MB)
         double spaceDifferenceMB = (size1 - size2) / (1024.0 * 1024.0);

        // Calculate the percentage difference
        double percentageDifference = ((double) (size2 - size1) / size1) * 100.0;

        return spaceDifferenceMB;
    }
}

