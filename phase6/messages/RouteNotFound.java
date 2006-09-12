package messages;

public class RouteNotFound extends Message
{
	public int htl; // Hops to live for backtracking
	
	public RouteNotFound (int id, int htl)
	{
		this.id = id;
		this.htl = htl;
		size = Message.HEADER_SIZE;
	}
	
	public String toString()
	{
		return new String ("route not found (" + id + "," + htl + ")");
	}
}
