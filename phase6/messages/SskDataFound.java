package messages;

public class SskDataFound extends Message
{
	public final int data;
	
	public SskDataFound (int id, int data)
	{
		this.id = id;
		this.data = data;
		size = Message.HEADER_SIZE + Message.DATA_SIZE;
	}
	
	public String toString()
	{
		return new String ("SSK data found (" + id + "," + data + ")");
	}
}
