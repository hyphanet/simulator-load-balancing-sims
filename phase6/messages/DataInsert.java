package messages;

public class DataInsert extends Message
{
	public DataInsert (int id)
	{
		this.id = id;
		size = Message.HEADER_SIZE;
	}
	
	public String toString()
	{
		return new String ("data insert (" + id + ")");
	}
}
