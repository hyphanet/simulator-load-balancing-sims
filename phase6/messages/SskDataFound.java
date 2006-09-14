package messages;

public class SskDataFound extends Message
{
	public SskDataFound (int id)
	{
		this.id = id;
		size = Message.HEADER_SIZE + Message.DATA_SIZE;
	}
	
	public String toString()
	{
		return new String ("SSK data found (" + id + ")");
	}
}
