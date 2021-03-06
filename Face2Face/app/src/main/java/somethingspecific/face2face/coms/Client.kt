package somethingspecific.face2face.coms

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.webrtc.*
import somethingspecific.face2face.events.EmptyEvent
import somethingspecific.face2face.events.Event
import somethingspecific.face2face.events.EventManager



public class Client(address:String, username:String, email:String) {

    //Great thing about these events is that they're two-way!
    //Outbound
    public val ClientListEvent = Event<Array<ClientParameters>>()
    public val OfferReceivedEvent = EmptyEvent()
    public val PartnerCloseEvent = EmptyEvent()

    //Inbound
    public val UserOfferResponseEvent = Event<Boolean>()

    companion object {
        var instance: Client? = null
        fun instance():Client {
            if(instance == null) {
                throw Exception("Client has not be initialized!")
            }
            return instance!!
        }
    }

    private val TAG = "ClientManager"

    public var params: ClientParameters

    private var events: EventManager
    private var signal: SignalClient
    private var stream: StreamClient?
    private var target: ClientParameters?
    private var lastSdp: SessionDescription?

    private var peers: ArrayList<ClientParameters>

    init{
        lastSdp = null
        target = null
        peers = ArrayList()
        params = ClientParameters(username, email,
            "https://scontent-lax3-1.xx.fbcdn.net/v/t1.0-9/5616975" +
                    "1_2328522497199217_8528485562888224768_n.jpg?_nc_cat=10" +
                    "6&_nc_ht=scontent-lax3-1.xx&oh=af5534402e7907046a72134c12382" +
                    "81c&oe=5D4AF933",
            "Online")
        events = EventManager()
        signal = SignalClient(events, address)
        stream = null//StreamClient(null,  null, null, Events)

        events.OpenEvent += { onOpen() }
        events.MessageEvent += { onMessage(it) }
        events.ClosedEvent += { onClose() }

        events.ConnectedEvent += { onConnectedEvent() }
        events.DisconnectedEvent += { onDisconnectedEvent() }
        events.RtcClosedEvent += { onRtcClosedEvent() }
        events.IceConnectedEvent += { onIceConnectedEvent() }
        events.IceDisconnectedEvent += { onIceDisconnectedEvent() }
        events.IceCandidateEvent += { onIceCandidateEvent(it) }
        events.IceCandidateRemovedEvent += { onIceCandidateRemovedEvent(it) }
        events.OfferEvent += { onLocalDescriptionEvent(it) }
        events.ReplyEvent += { onRemoteDescriptionEvent(it) }
        events.AddMediaEvent += { onMediaEvent(it) }
        events.RtcStatsReady += { onRtcStatsReady(it) }
        events.RtcConnectionError += { onRtcConnectionError(it) }


        signal.connect()

    }

    public fun reInit() {
        signal.send(MessageFactory.Info(params.email, params.username, params.avatar))
    }

    public fun hasTarget():Boolean {
        if(target == null)
            return false
        return true
    }

    public fun setTarget(client: ClientParameters):Boolean {
        if(client.email == params.email)
            return false
        target = client
        return true
    }

    public fun call(context: Context, root: EglBase, local: SurfaceViewRenderer, remote: SurfaceViewRenderer) {
        stream = StreamClient(context, root, events, local, remote)
        //This means we're making an outbound call
        if(target != null){
            //This means we're taking an inbound call
            if(lastSdp == null) {
                stream!!.createOffer()
            }
            else {
                stream!!.handleOffer(lastSdp!!)
            }
        }
    }

    public fun closePartner() {
        signal.send(MessageFactory.Close(params.email, target?.email ?: ""))
    }

    public fun hangup() {
        stream?.dispose()
        target = null
        lastSdp = null
        stream = null
    }


    private fun onOpen() {
        Log.d(TAG, "Client connected!")
        signal.send(MessageFactory.Info(params.email, params.username, params.avatar))
    }

    private fun onMessage(raw: String) {
        Log.d(TAG, "Got message: $raw")
        val msg = JSONObject(raw)
        when (msg.getString("type")) {
            "List" -> onList(msg.getJSONObject("clients"))

            "Offer" -> onOffer(msg.getString("sender"),
                SessionDescription(
                    SessionDescription.Type.OFFER,
                    msg.getString("offer")))

            "Reply" -> onReply(msg.getString("sender"),
                SessionDescription(
                    SessionDescription.Type.ANSWER,
                    msg.getString("reply")))

            "Ice" -> onIce(msg.getString("sender"),
                IceCandidate(
                    msg.getString("iceMid"),
                    msg.getInt("iceIndex"),
                    msg.getString("ice")))

            "Close" -> PartnerCloseEvent()
        }
    }

    private fun onList(list:JSONObject) {
        peers.clear()
        list.keys().forEach {
            val client = list.getJSONObject(it)
            val email = client.getString("email")
            val user = client.getString("user")
            val avatar = client.getString("avatar")
            val status = client.getString("status")
            if(email != params.email)
                peers.add(ClientParameters(user, email, avatar, status))
        }
        ClientListEvent(peers.toTypedArray())
    }

    private fun onOffer(targetId:String, offer:SessionDescription) {
        Log.d(TAG, "Got offer")
        if(stream == null) {
            target = peers.find{ peer -> peer.email == targetId}
            if(target == null) {
                target = ClientParameters("Unknown", targetId, "SomeDefault.jpg", "Unknown")
            }
            //TODO : push a request for a call from targetId then wait for user input
            lastSdp = offer
            OfferReceivedEvent()
        }
        //TODO: else - Reject the offer since a call must be under way
    }

    private fun onReply(target:String, reply:SessionDescription) {
        Log.d(TAG, "Got reply")
        stream?.handleReply(reply)

    }

    private fun onIce(target:String, ice:IceCandidate) {
        stream?.addIce(ice)
    }

    private fun onClose() {
        Log.d(TAG, "Client disconnected!")
        hangup()
    }

    private fun onLocalDescriptionEvent(offer: SessionDescription) {
        val msg = MessageFactory.Offer(params.email, target!!.email, offer)
        signal.send(msg)
    }


    private fun onRemoteDescriptionEvent(reply: SessionDescription) {
        val msg = MessageFactory.Reply(params.email, target!!.email, reply)
        signal.send(msg)
    }

    private fun onIceCandidateEvent(ice: IceCandidate){
        val msg = MessageFactory.Ice(params.email, target!!.email, ice)
        signal.send(msg)
    }


    private fun onMediaEvent(medias : Array<MediaStream>) {
        stream?.let{
            for(media in medias)
            it.addStream(media)
        }
    }


    //TODO: Implement all of these!
    private fun onConnectedEvent() {}
    private fun onDisconnectedEvent() {}
    private fun onRtcClosedEvent() {}
    private fun onIceConnectedEvent() {}
    private fun onIceDisconnectedEvent() {}
    private fun onIceCandidateRemovedEvent(candidates: Array<IceCandidate>) {}
    private fun onRtcStatsReady(stats: Array<StatsReport>) {}
    private fun onRtcConnectionError(error: String) {}

}