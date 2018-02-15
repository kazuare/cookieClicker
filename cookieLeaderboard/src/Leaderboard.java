

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

@WebServlet("/Leaderboard")
public class Leaderboard extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private boolean queueInitialized = false;
	
	Map<String, MutableBucket> leaderboard; 
	
	private Channel channel;
	private Connection connection;

	volatile String lastMessage = "none";
	
    @Override
    public void init(){
    	leaderboard = new ConcurrentHashMap<>();

    	leaderboard.put("rocky", new MutableBucket(100, 1));
    	leaderboard.put("mock", new MutableBucket(50, 2));
    	
    	ConnectionFactory factory = new ConnectionFactory();
    	factory.setHost("localhost");
		try {
			connection = factory.newConnection();
	    	channel = connection.createChannel();
	    	
	    	channel.queueDeclare("events", false, false, false, null);
	    	channel.queueDeclare("updates", false, false, false, null);
	    	channel.queueDeclare("updatesForFactories", false, false, false, null);
	    		    	
	    	Consumer consumer = new DefaultConsumer(channel) {
	    		  @Override
	    		  public void handleDelivery(String consumerTag, Envelope envelope,
	    		                             AMQP.BasicProperties properties, byte[] body)
	    		      throws IOException {
	    		    String message = new String(body, "UTF-8");
	    		    String username = message.substring(0, message.indexOf(';'));
	    		    long cookieAddition = Long.parseLong(
	    		    	message.substring(
	    		    		message.indexOf(';')+1,
	    		    		message.indexOf(";;")
	    		    	)
	    		    );
	    		    long factoryAddition = Long.parseLong(
		    		    message.substring(
		    		    	message.indexOf(";;")+2	    		    	
		    		    )
		    		);
	    		    
	    		    lastMessage = "user: " + username + 
	    		    		" cookieAddition: " + cookieAddition +
	    		    		" factoryAddition: " + factoryAddition;
	    		    
	    		    if (!leaderboard.containsKey(username))
	    		    	leaderboard.put(username, new MutableBucket(0,0));
	    		    
	    		    MutableBucket bucket = leaderboard.get(username);
	    		    
	    		    synchronized (bucket) {
	    		    	if (bucket.cookieCount + cookieAddition >= 0) {
	    		    		bucket.cookieCount += cookieAddition;
	    		    		bucket.factoryCount += factoryAddition;
	    		    	}
	    		    }
	    		  }
	    	};
	    	
	    	channel.basicConsume("events", true, consumer);
	    	
	    	Timer timer = new Timer();
	    	TimerTask task = new TimerTask(){
				@Override
				public void run() {
					try {
						sendUpdate();
					} catch (Exception e) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						e.printStackTrace(pw);
						String sStackTrace = sw.toString(); // stack trace as a string
						lastMessage = sStackTrace;
					}
					
				}	    		
	    	};
	    	timer.schedule(task, 0L ,250L);
	    	
	    	queueInitialized = true;
	    	
		} catch (IOException | TimeoutException e) {	
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			String sStackTrace = sw.toString(); // stack trace as a string
			lastMessage = sStackTrace;		
		}
    }
    
    @Override
    public void destroy(){
    	try {
			channel.close();
			connection.close();
		} catch (IOException | TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    @Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (queueInitialized) {
			response.getWriter().append(lastMessage).close();
		} else {
			response.getWriter().append("ERROR !queueInitialized").close();
		}
	}
    
    private void sendUpdate() throws IOException {
    	ByteArrayOutputStream bos = new ByteArrayOutputStream();
		Map<String, String> hmap = new HashMap<>();
		for (Map.Entry<String, MutableBucket> e: leaderboard.entrySet()) {
			hmap.put(e.getKey(), ""+e.getValue());
		}

		ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(hmap);
        oos.flush();
        oos.close();       
        
        byte[] bytes = bos.toByteArray();
        channel.basicPublish("", "updates", null, bytes);
        channel.basicPublish("", "updatesForFactories", null, bytes);
	}
}
