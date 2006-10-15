package messages;

public class DataNotFound extends Message
{
	public DataNotFound (int id)
	{
		this.id = id;
	}
	
	public String toString()
	{
		return new String ("data not found (" + id + ")");
	}
}
