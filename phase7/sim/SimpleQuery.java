//
// Queries to track network traffic
//
package sim;

class SimpleQuery {

    // Message types
    public static final int SUCCESS=0;
    public static final int DEADEND=1;
    public static final int HTLSTOP=2;
    public static final int REJECTLOOP=3;
    public static final int FORWARD=4;

    final SimpleNode source;
    final SimpleNode dest;
    final SimpleNode target;
    final SimpleLink link;
    final int type;
    int htl;
    boolean openref;

    /*
     * @source: sender of query
     * @dest: destination of query
     * @target: ultimate target of query (ignored if without meaning)
     * @l: the link over which the query is sent
     * @type: type of query
     * @htl: HTL left on query
     * @opennet: if this contains an opennet reference (from a successful CHK)
     */
    public SimpleQuery (SimpleNode source, SimpleNode dest, SimpleNode target, SimpleLink l, int type, int htl, boolean opennet) {
	this.source = source;
	this.dest = dest;
	this.target = target;
	this.link = l;
	this.type = type;
	this.htl = htl;
	this.openref = opennet;
    }

    public boolean internalQuery() {
	return (link==null);
    }
}
