package messages;

public class DataNotFound extends Message
{
	public DataNotFound (int id)
	{
		this.id = id;
		size = Message.HEADER_SIZE;
	}
	
	public String toString()
	{
		return new String ("data not found (" + id + ")");
	}
}
