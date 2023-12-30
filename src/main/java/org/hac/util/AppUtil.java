package org.hac.util;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkus.runtime.Startup;


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

    String configData = null;


        private  Region region = Region.EU_WEST_1;
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
        System.out.println("value from parameter Store :"+configData.length());
        if(configData != null && !configData.isEmpty()){
            try {
                 jsonNode = objectMapper.readTree(configData);
                 localCompressFolder = jsonNode.at("/lambda/localCOMPfolder").asText();
                 awsRegion = jsonNode.at("/aws/region").asText();
                 compressionScale = jsonNode.at("/img/compressionScale").asDouble();
                 imgQuality = jsonNode.at("/img/quality").asDouble();
                System.out.println("from SSM JSON: "+localCompressFolder + " | " + compressionScale + " | " + imgQuality);
            } catch (JsonProcessingException e) {
               
                e.printStackTrace();
            }
       }
    }

    public String getValuefromJSONPath(String path) {
        return jsonNode.at(path).asText();
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

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
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



}
