
public class MutableBucket {
	public long cookieCount = 0;
	public long factoryCount = 0;
	
	public MutableBucket(long cookieCount, long factoryCount){
		this.cookieCount = cookieCount;
		this.factoryCount = factoryCount;
	}
	
	public String toString(){
		return cookieCount+";"+factoryCount;
	}
}
