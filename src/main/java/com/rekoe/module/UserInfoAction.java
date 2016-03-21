package com.rekoe.module;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.error.OAuthError;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.OAuthResponse;
import org.apache.oltu.oauth2.common.message.types.ParameterStyle;
import org.apache.oltu.oauth2.common.utils.OAuthUtils;
import org.apache.oltu.oauth2.rs.request.OAuthAccessResourceRequest;
import org.apache.oltu.oauth2.rs.response.OAuthRSResponse;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.mvc.annotation.At;
import org.nutz.mvc.annotation.By;
import org.nutz.mvc.annotation.Filters;
import org.nutz.mvc.annotation.GET;
import org.nutz.mvc.annotation.Ok;

import com.rekoe.domain.OAuthUser;
import com.rekoe.filter.OauthCrossOriginFilter;
import com.rekoe.service.OAuthService;
import com.rekoe.service.OAuthUserService;
import com.rekoe.utils.Constants;

@IocBean
@At("/v1/openapi")
public class UserInfoAction {

	@Inject
	private OAuthService oAuthService;

	@Inject
	private OAuthUserService oAuthUserService;

	/**
	 * @api {get} /v1/openapi/userinfo 获取账号详细
	 *@apiSampleRequest http://warlogin.shanggame.com/v1/openapi/userinfo
	 * @apiGroup User
	 * @apiVersion 1.0.0
	 *
	 *
	 * @apiParam {String} access_token 为上一步获取的access_token
	 * 
	 * @apiSuccess {long} uid 用户id
	 * @apiSuccess {String} name 用户名
	 * @apiSuccess {boolean} [locked] 是否锁定
	 *
	 */
	
	@At
	@Ok("json")
	@GET
	@Filters(@By(type = OauthCrossOriginFilter.class))
	public Object userinfo(HttpServletRequest request, HttpServletResponse res) throws OAuthSystemException, OAuthProblemException {
		return checkAccessToken(request, res);
	}

	/**
	 * 不校验accessToken
	 * 
	 * @param request
	 * @return
	 * @throws OAuthSystemException
	 * @throws OAuthProblemException
	 */
	public OAuthUser nocheckAccessToken(HttpServletRequest request) throws OAuthSystemException, OAuthProblemException {
		// 构建OAuth资源请求
		OAuthAccessResourceRequest oauthRequest = new OAuthAccessResourceRequest(request, ParameterStyle.QUERY);
		// 获取Access Token
		String accessToken = oauthRequest.getAccessToken();
		// 获取用户名
		String username = oAuthService.getUsernameByAccessToken(accessToken);
		OAuthUser user = oAuthUserService.findByUsername(username);
		return user;
	}

	/**
	 * 校验accessToken
	 * 
	 * @param request
	 * @return
	 * @throws OAuthSystemException
	 */
	private Object checkAccessToken(HttpServletRequest request, HttpServletResponse res) throws OAuthSystemException {
		try {
			// 构建OAuth资源请求
			OAuthAccessResourceRequest oauthRequest = new OAuthAccessResourceRequest(request, ParameterStyle.QUERY);
			// 获取Access Token
			String accessToken = oauthRequest.getAccessToken();
			// 验证Access Token
			if (!oAuthService.checkAccessToken(accessToken)) {
				// 如果不存在/过期了，返回未验证错误，需重新验证
				OAuthResponse oauthResponse = OAuthRSResponse.errorResponse(HttpServletResponse.SC_UNAUTHORIZED).setRealm(Constants.RESOURCE_SERVER_NAME).setError(OAuthError.ResourceResponse.INVALID_TOKEN).buildHeaderMessage();
				res.addHeader("Content-Type", "application/json; charset=utf-8");
				Status status = new Status();
				status.setCode(Constants.HTTPSTATUS_UNAUTHORIZED);
				status.setMsg(Constants.INVALID_ACCESS_TOKEN);
				res.setStatus(oauthResponse.getResponseStatus());
				return status;
			}
			// 获取用户名
			String username = oAuthService.getUsernameByAccessToken(accessToken);
			OAuthUser user = oAuthUserService.findByUsername(username);
			return user;
		} catch (OAuthProblemException e) {
			// 检查是否设置了错误码
			String errorCode = e.getError();
			if (OAuthUtils.isEmpty(errorCode)) {
				OAuthResponse oauthResponse = OAuthRSResponse.errorResponse(HttpServletResponse.SC_UNAUTHORIZED).setRealm(Constants.RESOURCE_SERVER_NAME).buildHeaderMessage();
				res.addHeader(OAuth.HeaderType.WWW_AUTHENTICATE, oauthResponse.getHeader(OAuth.HeaderType.WWW_AUTHENTICATE));
				res.setStatus(Constants.HTTPSTATUS_UNAUTHORIZED);
				return null;
			}
			OAuthResponse oauthResponse = OAuthRSResponse.errorResponse(HttpServletResponse.SC_UNAUTHORIZED).setRealm(Constants.RESOURCE_SERVER_NAME).setError(e.getError()).setErrorDescription(e.getDescription()).setErrorUri(e.getUri()).buildHeaderMessage();
			res.addHeader(OAuth.HeaderType.WWW_AUTHENTICATE, oauthResponse.getHeader(OAuth.HeaderType.WWW_AUTHENTICATE));
			res.setStatus(Constants.HTTPSTATUS_BAD_REQUEST);
			return null;
		}
	}
}