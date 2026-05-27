package com.yuntuku.yunbackend.manager.websocket.disruptor;

import com.yuntuku.yunbackend.manager.websocket.model.PictureEditRequestMessage;
import com.yuntuku.yunbackend.model.entity.User;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

@Data
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;
    
    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;

}
