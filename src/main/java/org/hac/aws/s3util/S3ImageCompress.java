package org.hac.aws.s3util;

import com.googlecode.pngtastic.core.PngImage;
import com.googlecode.pngtastic.core.PngOptimizer;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import net.coobird.thumbnailator.Thumbnails;


@ApplicationScoped
public class S3ImageCompress {

    private  Region region = Region.EU_WEST_1;
    private AwsCredentialsProvider credentialsProvider;
    private  S3Client s3client;

    void startup(@Observes StartupEvent event) {
    }

    S3ImageCompress(){
        credentialsProvider = EnvironmentVariableCredentialsProvider.create();

        s3client = S3Client.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();
        System.out.println("Clients initialized ..");
    }



    /*
    public static double imgCompressionScale = 0.50;
    public static double outputQuality = 0.2; // 0.0 to 1.0 , e.g 0.9 is 90% quality

    public static String destFolder = "/tmp/compress/";
    public static String SRCFolder = "/tmp/";

    //public static String destBucketName = "admin.beehive.holiday";
    public static String destBucketName = "amrootdata--eun1-az1--x-s3";
*/
    @Inject
    @ConfigProperty(name = "lambda.localCOMPfolder", defaultValue = "/tmp/compress/")
    private String localCompressFolder;

    @Inject
    @ConfigProperty(name = "lambda.localSRCFolder", defaultValue = "/tmp/")
    private  String localSRCFolder;

    @Inject
    @ConfigProperty(name = "s3.destBucketName", defaultValue = "amrootdata--eun1-az1--x-s3")
    private  String s3DestBucketName;


    @Inject
    @ConfigProperty(name = "s3.SRCBucketname", defaultValue = "ameenbayt")
    private  String s3SRCBucketname;

    @Inject
    @ConfigProperty(name = "s3.SRCBucketPrefix", defaultValue = "/pics/")
    private  String s3SRCBucketPrefix;

    @Inject
    @ConfigProperty(name = "aws.region", defaultValue = "eu-west-1")
    private  String awsRegion;

    @Inject
    @ConfigProperty(name = "img.compressionScale", defaultValue = "0.50")
    private  double compressionScale;

    @Inject
    @ConfigProperty(name = " img.quality", defaultValue = "0.2")
    private  double imgQuality;

    public  void compressS3File(String bucketName,String keyName){

        File S3File = getFileFromS3(bucketName, keyName);
        String  compS3File = compressLocalFile(S3File);
        boolean status = updateS3(s3DestBucketName,compS3File,S3File);

    }

    public  File getFileFromS3(String bucketName,String keyName){
        long startTime = System.currentTimeMillis();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        // Extract the file name with extension
        String fileNameWithExtension = "";
        String pathBeforeFileName = "";
        if(keyName.contains("/")) {
             fileNameWithExtension = keyName.substring(keyName.lastIndexOf('/') + 1);
             pathBeforeFileName = keyName.substring(0, keyName.lastIndexOf('/'));
            System.out.println("file with multiple prefixes ");
        }else
            fileNameWithExtension = keyName;

        // Extract the path before the file name
        File baseTempDir = new File(localSRCFolder+pathBeforeFileName+"/");
        baseTempDir.mkdirs();
        System.out.println("Directories Created :"+baseTempDir.getAbsolutePath());

        File myFile = new File(localSRCFolder+keyName);

        try{
            ResponseBytes<GetObjectResponse> objectBytes = s3client.getObjectAsBytes(getObjectRequest);
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
        System.out.println("InWork-createTT");

        File destinationDir = new File(localCompressFolder);
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
                        .scale(compressionScale)
                        .outputQuality(imgQuality)
                        .toFile(destFile);
                System.out.println("Compressed img file :"+destFile);
            } catch (IOException ioe) {
                System.out.println(ioe.getMessage());
            }
      //  } if for png
        System.out.println("compressLocalFile took: "+TimeUnit.MILLISECONDS.toMillis(System.currentTimeMillis() - startTime));
        return destFile;
    }

    private  boolean updateS3(String bucketName, String compS3File,File ogFile) {
        long startTime = System.currentTimeMillis();
        System.out.println("updating S3 location :"+compS3File);
        PutObjectRequest putRequest =  PutObjectRequest.builder()
                .bucket(bucketName)
                .key(ogFile.getName())
                .build();

        PutObjectResponse putResponse = s3client.putObject(putRequest, Paths.get(compS3File));
        System.out.println("eTag : "+putResponse.eTag());
        System.out.println("updateS3 took: "+TimeUnit.MILLISECONDS.toMillis(System.currentTimeMillis() - startTime));
        if (putResponse == null) return false;
        else {
            ogFile.delete();
            new File(compS3File).delete();
            System.out.println("Files Deleted ....");
            return true;
        }
    }
}

