package faas.cloud.demo.Events;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.TableCollection;
import com.amazonaws.services.dynamodbv2.document.spec.PutItemSpec;
import com.amazonaws.services.dynamodbv2.model.ListTablesResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.simpleemail.*;
import com.amazonaws.services.simpleemail.model.Destination;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
//import com.amazonaws.services.sns.model.PublishRequest;
//import com.amazonaws.services.sqs.AmazonSQS;
//import com.amazonaws.services.sqs.AmazonSQSClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;

import java.time.Instant;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class EmailTask { //implements RequestHandler<SNSEvent,Object>

    private DynamoDB dynamoDB;
    public String dynamodbTable;
    public String QUEUE = "emailQ";
    public String domain = "prod.chandanawebapp.me";
    public String SENDER_EMAIL = "no-reply@" + domain;


    private static final String EMAIL_SUBJECT="Reset Password";
    private static final String EMAIL_TEXT = "The link to reset your password :- ";

    public EmailTask() {
        AmazonDynamoDBClient dynamoClient = new AmazonDynamoDBClient();
        dynamoClient.setRegion(Region.getRegion(Regions.US_EAST_1));
        this.dynamoDB = new DynamoDB(dynamoClient);
        System.out.println("Created DynamoDB client");
    }

    public Object handleRequest(SNSEvent input, Context context) {
        try{
            System.out.println("Entered EmailTask handleRequest function");

//            AWSCredentials awsCreds = new BasicAWSCredentials(" "," ");
//            AmazonDynamoDB c = new AmazonDynamoDBClient(awsCreds);
//            this.dynamoDB = new DynamoDB(c);
            context.getLogger().log("Entered Lambda Function Code");
//            String emailVal = "";
//            String token = UUID.randomUUID().toString();
//            emailVal += "<p><a href='#'>http://" + domain +"/forgotPWD/"+token+ "</a></p><br>";
//            emailVal =  emailVal.replaceAll("\"","");
//            context.getLogger().log(emailVal);
//            sendEMAIL(context,"chandana.sri.g@gmail.com",emailVal);
            if(dynamoDB == null)
                context.getLogger().log("Dynamo db object is null");
            TableCollection<ListTablesResult> dbTables = dynamoDB.listTables();
            Iterator<Table> iterator = dbTables.iterator();
            while (iterator.hasNext()) {
                Table table1 = iterator.next();
                context.getLogger().log("Dynamodb table name:- " + table1.getTableName());
            }
            context.getLogger().log("Fetching Dynamo db Table");
            Table table = dynamoDB.getTable("csye6225");



            context.getLogger().log("Fetching msgs from queue");
            context.getLogger().log(input.toString());
            context.getLogger().log(input.getRecords().get(0).toString());
            context.getLogger().log("$$$$$$$$MESSAGE$$$$$$$$$$$");

            context.getLogger().log(input.getRecords().get(0).getSNS().getMessage());
            String requestsFromSQS = input.getRecords().get(0).getSNS().getMessage();
            String toEMAIL = requestsFromSQS.split(",")[0];
            context.getLogger().log("TO EMAIL: "+toEMAIL);
            String token = requestsFromSQS.split(",")[1];
            context.getLogger().log("token:-"+token);

            Item item = dynamoDB.getTable("csye6225").getItem("id", toEMAIL);
            long ttlTime = Instant.now().getEpochSecond() + 15*60;
            if ((item != null && Long.parseLong(item.get("ttl").toString()) < Instant.now().getEpochSecond() || item == null)) {
             //   Item itemToAdd = new Item().withString("id", toEMAIL).withLong("ttl", ttlTime);
                PutItemSpec item2 = new PutItemSpec().withItem(new Item()
                        .withPrimaryKey("id", toEMAIL)
                        .withString("token", token)
                        .withLong("ttl", ttlTime));
                dynamoDB.getTable("csye6225").putItem(item2);

                String emailVal = "";
                //token = UUID.randomUUID().toString();
                emailVal += "<p><a href='#'>https://" + domain +"/reset?email="+toEMAIL+"&token="+token+ "</a></p><br>";
                emailVal =  emailVal.replaceAll("\"","");
                context.getLogger().log(emailVal);
                sendEMAIL(context,toEMAIL,emailVal);
            }


        }
        catch (Exception ex){
            context.getLogger().log("Email sending failed. Error message: "
                    + ex.getMessage());
        }
        return null;
    }

    //Sending email through amazon SES
    public void sendEMAIL(Context context, String toEMAIL, String emailVal){
        try {
            context.getLogger().log("Sending Email");
            AWSCredentials awsCreds = new BasicAWSCredentials(" "," ");
            AmazonSimpleEmailService client = new AmazonSimpleEmailServiceClient(awsCreds);
            client.setRegion(Region.getRegion(Regions.US_EAST_1));

//            AmazonSimpleEmailService client =
//                    AmazonSimpleEmailServiceClientBuilder.standard()
//                            .withRegion(Regions.US_EAST_1)
//                            .build();
          //  System.out.println("Connected to Amazon SES!");
            context.getLogger().log("Connected to Amazon SES!");

            SendEmailRequest req = new SendEmailRequest()
                    .withDestination(new Destination()
                            .withToAddresses(toEMAIL))
                    .withMessage(new Message()
                            .withBody(new Body()
                                    .withHtml(new Content()
                                            .withCharset("UTF-8")
                                            .withData( EMAIL_TEXT +" <br/>" + emailVal)))
                            .withSubject(new Content()
                                    .withCharset("UTF-8")
                                    .withData(EMAIL_SUBJECT)))
                    .withSource(SENDER_EMAIL);

            SendEmailResult response = client.sendEmail(req);
            context.getLogger().log("Email successfully sent with result: " + response);

        } catch (Exception ex) {
            context.getLogger().log("Email sending failed. Error message: "
                    + ex.getMessage());

        }
    }

//    public void getMsgFrmSQS(AWSCredentials awsCreds,Context context){
//        context.getLogger().log("Getting msgs from SQS");
//        AmazonSQS sqsClient;
//        sqsClient = new AmazonSQSClient(awsCreds);
//        sqsClient.setRegion(Region.getRegion(Regions.US_EAST_1));
//      //  String queueUrl = sqsClient.getQueueUrl(QUEUE).getQueueUrl();
//        context.getLogger().log("SQS client created");
//
//
//
//        List<com.amazonaws.services.sqs.model.Message> receivedMessageList =
//                sqsClient.receiveMessage(sqsClient.getQueueUrl(QUEUE).getQueueUrl()).getMessages();
//        for(com.amazonaws.services.sqs.model.Message message : receivedMessageList) {
//            if (message.getBody() !=null && !message.getBody().isEmpty()) {
//                context.getLogger().log("Extracting recipient email id to send emails");
//                JsonObject jsonObject = new JsonParser().parse(message.getBody()).getAsJsonObject();
//                String toEMAIL = jsonObject.get("email").getAsString();
//
//                checkDynamoDBTable( context, toEMAIL, token);
//                sendEMAIL(context,toEMAIL,emailVal);
//
//            }
//        }
//    }

//    public void checkDynamoDBTable(Context context,String toEMAIL,String token){
//        context.getLogger().log("Reached checkDynamoDBTable");
//        // unix time
//        long now = Calendar.getInstance().getTimeInMillis()/1000;
//        //setting TTL to 60 minutes
//        long TTL = 15 * 60;
//        long totalTTL = TTL + now ;
//
//        Table table = dynamoDB.getTable(dynamodbTable);
//        if(table == null) {
//            context.getLogger().log("DynamoDB table not found");
//        }
//        else {
//            context.getLogger().log("checking in table for token for email: "+toEMAIL);
//            long ttlDBValue = 0;
//            Item item = table.getItem("email", toEMAIL);
//
//            if (item != null)
//                ttlDBValue = item.getLong("ttl");
//
//            if(item == null || (ttlDBValue < now && ttlDBValue != 0)) {
//                System.out.println("creating new token and sending email to: "+toEMAIL);
//                context.getLogger().log("ttl expired, creating new token and sending email");
//                table.putItem( new PutItemSpec().withItem(new Item()
//                        .withPrimaryKey("email", toEMAIL)
//                        .withString("token", token)
//                        .withLong("ttl", totalTTL)));
//
//                context.getLogger().log("AWS request ID:" + context.getAwsRequestId());
//                //context.getLogger().log("AWS message ID:" + input.getRecords().get(0).getSNS().getMessageId());
//            } else {
//                context.getLogger().log(item.toJSON() + "Email already sent!");
//            }
//        }
//    }
}
