package mediaengine.fritt.multiclientdemo;

import java.util.LinkedList;
import java.util.Random;

public class MCMessageManager {
    public LinkedList<MessageHandler> messageHandlers;
    public long handle_id;
    public String service_name;
    public String opaque_id;
    public String record_name;

    public String service_type;

    public MCMessageManager(){
        messageHandlers = new LinkedList<MessageHandler>();
    }

    public static class MessageHandler{
        private final String mms;
        private final String transaction;

        public MessageHandler(String mms){
            this.mms = mms;
            this.transaction = getRandomString(12);
        }

        public String getMms(){return mms;}

        public String getTransaction(){return transaction;}
    }


    public static String getRandomString(int length){
        String str="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random=new Random();
        StringBuffer sb=new StringBuffer();
        for(int i=0;i<length;i++){
            int number=random.nextInt(62);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }
}
