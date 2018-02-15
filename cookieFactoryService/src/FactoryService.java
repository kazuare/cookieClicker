

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
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

@WebServlet("/FactoryService")
public class FactoryService extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private boolean queueInitialized = false;
	
	Map<String, Long> factories; 
	
	private Channel channel;
	private Connection connection;

	volatile String lastMessage = "none";
	
    @Override
    public void init(){
    	factories = new ConcurrentHashMap<>();
    	
    	ConnectionFactory factory = new ConnectionFactory();
    	factory.setHost("localhost");
		try {
			connection = factory.newConnection();
	    	channel = connection.createChannel();
	    	
	    	channel.queueDeclare("events", false, false, false, null);
	    	channel.queueDeclare("updatesForFactories", false, false, false, null);
	    		    	
	    	Consumer consumer = new DefaultConsumer(channel) {
	    		  @Override
	    		  public void handleDelivery(String consumerTag, Envelope envelope,
	    		                             AMQP.BasicProperties properties, byte[] body)
	    		      throws IOException {	    		    
	    		    ByteArrayInputStream bos = new ByteArrayInputStream(body);	    		    
	    		    ObjectInputStream ois = new ObjectInputStream(bos);
	    	        try {
						Map<String, String> map = (HashMap) ois.readObject();
						for (Map.Entry<String, String> e : map.entrySet()) {
							String value = e.getValue();
							factories.put(e.getKey(), 
									Long.parseLong(value.substring(value.indexOf(';')+1)));
						}
					} catch (ClassNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    	        ois.close();
	    		  }
	    	};
	    	
	    	channel.basicConsume("updatesForFactories", true, consumer);
	    	
	    	Timer timer = new Timer();
	    	TimerTask task = new TimerTask(){
				@Override
				public void run() {
					try {
						generateEvents();
					} catch (Exception e) {
						StringWriter sw = new StringWriter();
						PrintWriter pw = new PrintWriter(sw);
						e.printStackTrace(pw);
						String sStackTrace = sw.toString(); // stack trace as a string
						lastMessage = sStackTrace;
					}
					
				}	    		
	    	};
	    	timer.schedule(task, 0L ,1000L);
	    	
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
    
    private synchronized void generateEvents() throws IOException {
    	for (Map.Entry<String, Long> e : factories.entrySet())        
    		channel.basicPublish("", "events", null, (e.getKey()+";"+e.getValue()+";;0").getBytes());
	}
}
