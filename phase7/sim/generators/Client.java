package sim.generators;
import sim.messages.Search;

public interface Client
{
	public void searchStarted (Search s); // Callback
}
