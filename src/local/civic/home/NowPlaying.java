package local.civic.home;
import android.content.*;import android.media.*;import android.media.session.*;import java.util.*;
final class NowPlaying {
 static MediaController active(Context c){try{MediaSessionManager manager=(MediaSessionManager)c.getSystemService(Context.MEDIA_SESSION_SERVICE);if(manager==null)return null;List<MediaController> all=manager.getActiveSessions(new ComponentName(c,MediaAccess.class));for(MediaController m:all){PlaybackState state=m.getPlaybackState();if(state!=null&&state.getState()==PlaybackState.STATE_PLAYING)return m;}return all.isEmpty()?null:all.get(0);}catch(SecurityException e){return null;}}
 static String value(MediaMetadata m,String k){CharSequence s=m==null?null:m.getText(k);return s==null?"":s.toString();}
}
