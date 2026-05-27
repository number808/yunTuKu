package com.yuntuku.yunbackend.api.imagesearch.sub;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import com.yuntuku.yunbackend.api.imagesearch.model.ImageSearchResult;
import com.yuntuku.yunbackend.exception.BusinessException;
import com.yuntuku.yunbackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import cn.hutool.json.JSONArray;

import cn.hutool.json.JSONUtil;


import java.util.List;
import java.util.List;

@Slf4j
public class GetImageListApi {

    /**
     * 获取图片列表
     *
     * @param url
     * @return
     */
    public static List<ImageSearchResult> getImageList(String url) {
        try {
            // 发起GET请求
            HttpResponse response = HttpUtil.createGet(url).execute();

            // 获取响应内容
            int statusCode = response.getStatus();
            String body = response.body();

            // 处理响应
            if (statusCode == 200) {
                // 解析 JSON 数据并处理
                return processResponse(body);
            } else {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
        } catch (Exception e) {
            log.error("获取图片列表失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取图片列表失败");
        }
    }

    /**
     * 处理接口响应内容
     *
     * @param responseBody 接口返回的JSON字符串
     */
    private static List<ImageSearchResult> processResponse(String responseBody) {
        // 解析响应对象，把String转成JSON数据
        JSONObject jsonObject = new JSONObject(responseBody);
        if (!jsonObject.containsKey("data")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        /**
         * 把
         */
        JSONObject data = jsonObject.getJSONObject("data");
        if (!data.containsKey("list")) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未获取到图片列表");
        }
        JSONArray list = data.getJSONArray("list");
        /**
         * 把 JSON 数组 自动转换成 Java 的 List 对象列表
         *
         *
         *
         * {
         *   "contsign": "1489510890,668702531",
         *   "height": 330,
         *   "width": 440,
         *   "thumbUrl": "http://mms1.baidu.com/it/u=1786106512...",
         *   "fromUrl": "http://www.douyin.com/video/7206955903696833851",
         *   "objUrl": "https://graph.baidu.com/pcpage/similar?...",
         *   "index": 0,
         *   "page": 0
         * }
         * 变成ImageSearchResult{
         *     contsign = "1489510890,668702531",
         *     height = 330,
         *     width = 440,
         *     thumbUrl = "http://mms1.baidu.com/it/u=1786106512...",
         *     fromUrl = "http://www.douyin.com/video/7206955903696833851",
         *     objUrl = "https://graph.baidu.com/pcpage/similar?...",
         *     index = 0,
         *     page = 0
         * }
         */
        return JSONUtil.toList(list, ImageSearchResult.class);
    }

    public static void main(String[] args) {
        String url = "https://graph.baidu.com/ajax/pcsimi?carousel=503&entrance=GENERAL&extUiData%5BisLogoShow%5D=1&inspire=general_pc&limit=30&next=2&render_type=card&session_id=16250747570487381669&sign=1265ce97cd54acd88139901733452612&tk=4caaa&tpl_from=pc";
        List<ImageSearchResult> imageList = getImageList(url);
        System.out.println("搜索成功" + imageList);
    }
}
