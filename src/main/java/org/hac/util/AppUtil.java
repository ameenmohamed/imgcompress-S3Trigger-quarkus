package org.hac.util;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;


@Startup
@ApplicationScoped
 public class AppUtil {


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
    @ConfigProperty(name = "s3.destBucketPrefix", defaultValue = "/images/")
    private String destBucketPrefix;

    @Inject
    @ConfigProperty(name = "s3.SRCBucketname", defaultValue = "ameenbayt")
    private  String s3SRCBucketname;

    @Inject
    @ConfigProperty(name = "s3.SRCBucketPrefix", defaultValue = "/pics/")
    private  String s3SRCBucketPrefix;


    @Inject
    @ConfigProperty(name = "img.compressionScale", defaultValue = "0.50")
    private  double compressionScale;

    @Inject
    @ConfigProperty(name = " img.quality", defaultValue = "0.2")
    private  double imgQuality;

    @Inject
    @ConfigProperty(name = " img.validFileExtensions", defaultValue = "jpg, jpeg, png, gif, bmp, tiff, tif, webp, svg, raw, heic")
    private String validFileExtensions;

    String configData = null;


        private  Region region = Region.of(System.getenv("AWS_REGION"));
        private AwsCredentialsProvider credentialsProvider;
          public S3Client s3client;
         SsmClient ssmClient;

    AppUtil(){
        credentialsProvider = EnvironmentVariableCredentialsProvider.create();

        s3client = S3Client.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        ssmClient = SsmClient.builder().region(region)
                .credentialsProvider(credentialsProvider)
                .build();

        System.out.println("Clients initialized ..");
    }

    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode jsonNode ;

    @PostConstruct
    public  void loadConfig(){
         //get value from lambda Environment 
        String ssmConfigLocation =System.getenv("ssmConfigKey"); //"/hac/lambda/<functionname>/config"
        //get SSM Parameter value from SSM Parameterstoreclear
        configData = ssmClient.getParameter(GetParameterRequest.builder().name(ssmConfigLocation).build()).parameter().value();
        
    

    }

    public void readConfigData(){
        String COMM_SEP = " | ";
        System.out.println("value from parameter Store :"+configData.length());
        if(configData != null && !configData.isEmpty()){
            try {
                 jsonNode = objectMapper.readTree(configData);
                 localCompressFolder = jsonNode.at("/lambda/localCOMPfolder").asText();
                 compressionScale = jsonNode.at("/img/compressionScale").asDouble();
                 imgQuality = jsonNode.at("/img/quality").asDouble();
                 destBucketPrefix = jsonNode.at("/s3/destBucketPrefix").asText();
                 s3DestBucketName = jsonNode.at("/s3/destBucketName").asText();
                 s3SRCBucketPrefix = jsonNode.at("/s3/SRCBucketPrefix").asText();
                 validFileExtensions = jsonNode.at("/img/validFileExtensions").asText();
                System.out.println("from SSM JSON: "+localCompressFolder + COMM_SEP + compressionScale + COMM_SEP + imgQuality + COMM_SEP +s3SRCBucketPrefix);
            } catch (JsonProcessingException e) {
               
                e.printStackTrace();
            }
       }
    }

    public String getValuefromJSONPath(String path) {
        return jsonNode.at(path).asText();
    }

    public String getValidFileExtensions() {
        return validFileExtensions;
    }

    public void setValidFileExtensions(String validFileExtensions) {
        this.validFileExtensions = validFileExtensions;
    }

 public String getLocalCompressFolder() {
        return localCompressFolder;
    }

    public void setLocalCompressFolder(String localCompressFolder) {
        this.localCompressFolder = localCompressFolder;
    }

    public String getLocalSRCFolder() {
        return localSRCFolder;
    }

    public void setLocalSRCFolder(String localSRCFolder) {
        this.localSRCFolder = localSRCFolder;
    }

    public String getS3DestBucketName() {
        return s3DestBucketName;
    }

    public void setS3DestBucketName(String s3DestBucketName) {
        this.s3DestBucketName = s3DestBucketName;
    }

    public String getS3SRCBucketname() {
        return s3SRCBucketname;
    }

    public void setS3SRCBucketname(String s3srcBucketname) {
        s3SRCBucketname = s3srcBucketname;
    }

    public String getS3SRCBucketPrefix() {
        return s3SRCBucketPrefix;
    }

    public void setS3SRCBucketPrefix(String s3srcBucketPrefix) {
        s3SRCBucketPrefix = s3srcBucketPrefix;
    }

    public double getCompressionScale() {
        return compressionScale;
    }

    public void setCompressionScale(double compressionScale) {
        this.compressionScale = compressionScale;
    }

    public double getImgQuality() {
        return imgQuality;
    }

    public void setImgQuality(double imgQuality) {
        this.imgQuality = imgQuality;
    }

    public String getDestBucketPrefix() {
        return destBucketPrefix;
    }

    public void setDestBucketPrefix(String destBucketPrefix) {
        this.destBucketPrefix = destBucketPrefix;
    }

    /* 
     * function to validate keyName is a valid imagefile e.g uploads/prog1/someimg.jpeg 
     * returns false if it is not a valid file e.g uploads/prog1/ or uploads/prog1/file.pdf
    */
    public boolean validate(String keyName){

        String[] validFileExtensionsArr = validFileExtensions.split(",");
        for(int i=0;i<validFileExtensionsArr.length;i++){
            if(keyName.endsWith(validFileExtensionsArr[i].trim())){
                return true;
            }
        }
        return false;
    }

}
