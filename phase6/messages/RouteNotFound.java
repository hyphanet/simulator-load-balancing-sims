// Note: for the purposes of this simulation, RejectedLoop and RouteNotFound
// are equivalent

package messages;

public class RouteNotFound extends Message
{
	public RouteNotFound (int id)
	{
		this.id = id;
		size = Message.HEADER_SIZE;
	}
	
	public String toString()
	{
		return new String ("route not found (" + id + ")");
	}
}
