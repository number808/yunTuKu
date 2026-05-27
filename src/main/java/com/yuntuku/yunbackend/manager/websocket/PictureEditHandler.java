package com.yuntuku.yunbackend.manager.websocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.yuntuku.yunbackend.exception.ErrorCode;
import com.yuntuku.yunbackend.exception.ThrowUtils;
import com.yuntuku.yunbackend.manager.websocket.disruptor.PictureEditEventProducer;
import com.yuntuku.yunbackend.manager.websocket.model.PictureEditActionEnum;
import com.yuntuku.yunbackend.manager.websocket.model.PictureEditMessageTypeEnum;
import com.yuntuku.yunbackend.manager.websocket.model.PictureEditRequestMessage;
import com.yuntuku.yunbackend.manager.websocket.model.PictureEditResponseMessage;
import com.yuntuku.yunbackend.model.entity.User;
import com.yuntuku.yunbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket处理器类，在连接成功、连接关闭、接收到客户端消息时进行相应的处理。
 */
@Slf4j
@Component
public class PictureEditHandler extends TextWebSocketHandler {
    @Resource
    private UserService userService;
    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    @Resource
    @Lazy
    private PictureEditEventProducer pictureEditEventProducer;
    /**
     * 连接成功
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        log.info("WebSocket 已建立: userId={}, pictureId={}, sessionId={}", user.getId(), pictureId, session.getId());
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
       //加入通知消息
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给同一张图片的用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 收到前端发来的信息，根据信息处理请求
     * @param session
     * @param message（前端发来的信息）
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("WebSocket 收到文本消息: payload={}", message.getPayload());
        // 将消息解析为 PictureEditMessage
       PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从 Session 属性中获取公共参数
        Map<String ,Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        // 调用对应的消息处理方法
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage,session,user,pictureId);
    }

    /**
     * 用户退出编辑操作时，移除当前用户的编辑状态，并且向其他客户端发送消息
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     * @throws Exception
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }


    /**用户执行编辑操作时，将该操作同步给除了当前用户之外的其他客户端
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     * @throws Exception
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        //拿整个枚举类信息
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        ThrowUtils.throwIf(actionEnum==null, ErrorCode.PARAMS_ERROR,"操作不存在");

        //确认是否是当前编辑人
        if (editingUserId!=null && editingUserId.equals(user.getId()) ) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s开始执行%s", user.getUserName(),actionEnum.getValue());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            // 仅同步给其他客户端；若包含当前 session，前端易把回显当远端操作再次上报，造成死循环
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }

    }

    /**--------------这里踩了个坑----Java 受检异常强制处理规则----
     * // 这个方法抛出了 Exception，是受检异常
     * public void broadcastToPicture(...) throws Exception {
     *     // 发送消息会抛 IO 异常
     * }
     * 2. 当你在 private 方法里调用它：
     * private void handleEnterEditMessage(...) {
     *     broadcastToPicture(); // 调用了抛异常的方法
     * }
     * Java 语法规定：调用了抛受检异常的方法，要么捕获，要么在方法上声明 throws
     * 必须处理异常！
     *
     * 我没有处理异常（throws或者try catch），所以报错了
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     * @throws Exception
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        // 无人编辑：占位；或已是本人（重连/重复进入）：再广播一次 ENTER_EDIT，保持幂等
        if (editingUserId == null) {
            pictureEditingUsers.put(pictureId, user.getId());
        } else if (!editingUserId.equals(user.getId())) {
            PictureEditResponseMessage errorMsg = new PictureEditResponseMessage();
            errorMsg.setType(PictureEditMessageTypeEnum.ERROR.getValue());
            errorMsg.setMessage("已有用户正在编辑该图片");
            errorMsg.setUser(userService.getUserVO(user));
            session.sendMessage(new TextMessage(JSONUtil.toJsonStr(errorMsg)));
            return;
        }
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
        String message = String.format("%s开始编辑图片", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 必须发给同图所有连接（含自己），前端进入可编辑态
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }



    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws Exception {
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化：将 Long 类型转为 String，解决丢失精度问题
            /**
             *
             */
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance); // 支持 long 基本类型
            objectMapper.registerModule(module);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的 session 不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    // 全部广播，不排除任何session
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, @NotNull CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }



}
