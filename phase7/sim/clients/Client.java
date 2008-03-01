// This software has been placed in the public domain by its author

package sim.clients;
import sim.messages.Search;

public interface Client
{
	public void searchStarted (Search s); // Callback
}
