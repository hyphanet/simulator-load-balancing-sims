package messages;

public class SskDataFound extends Message
{
	public final int data;
	
	public SskDataFound (int id, int data)
	{
		this.id = id;
		this.data = data;
	}
	
	public int size()
	{
		return HEADER_SIZE + DATA_SIZE;
	}
	
	public String toString()
	{
		return new String ("SSK data found (" + id + "," + data + ")");
	}
}
