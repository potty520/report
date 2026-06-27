import { login, logout, getCurrentUserInfo, getCaptcha, getPublicKey } from '@/services/api';
import { setToken, clearToken, getToken, setCurrentUser, clearCurrentUser } from '@/utils/authority';
import { routerRedux } from 'umi';
import JSEncrypt from 'jsencrypt';

export default {
  namespace: 'global',

  state: {
    collapsed: false,
    currentUser: {},
    menuData: [],
    token: getToken() || '',
    captcha: '',
    publicKey: '',
  },

  effects: {
    *login({ payload }, { call, put }) {
      // 获取公钥加密密码
      const pubKeyRes = yield call(getPublicKey);
      let password = payload.password;
      if (pubKeyRes && pubKeyRes.data) {
        const encrypt = new JSEncrypt();
        encrypt.setPublicKey(pubKeyRes.data);
        password = encrypt.encrypt(payload.password);
      }

      const response = yield call(login, {
        username: payload.username,
        password,
        captcha: payload.captcha,
        uuid: payload.uuid,
      });

      if (response && response.code === 0) {
        yield put({
          type: 'changeLoginStatus',
          payload: {
            token: response.data.token,
            currentUser: response.data.user,
          },
        });

        const userRes = yield call(getCurrentUserInfo);
        if (userRes && userRes.data) {
          yield put({
            type: 'saveCurrentUser',
            payload: userRes.data,
          });
        }

        yield put(routerRedux.replace('/overview'));
      }
    },

    *logout(_, { call, put }) {
      yield call(logout);
      yield put({
        type: 'changeLoginStatus',
        payload: {
          token: '',
          currentUser: {},
        },
      });
      clearToken();
      clearCurrentUser();
      yield put(routerRedux.replace('/login'));
    },

    *getCaptcha(_, { call, put }) {
      const response = yield call(getCaptcha);
      if (response && response.data) {
        yield put({
          type: 'saveCaptcha',
          payload: response.data,
        });
      }
    },

    *fetchCurrentUser(_, { call, put }) {
      const response = yield call(getCurrentUserInfo);
      if (response && response.data) {
        yield put({
          type: 'saveCurrentUser',
          payload: response.data,
        });
      }
    },
  },

  reducers: {
    changeLayoutCollapsed(state, { payload }) {
      return { ...state, collapsed: payload };
    },

    changeLoginStatus(state, { payload }) {
      setToken(payload.token);
      if (payload.currentUser && Object.keys(payload.currentUser).length > 0) {
        setCurrentUser(payload.currentUser);
      }
      return {
        ...state,
        token: payload.token,
        currentUser: payload.currentUser || state.currentUser,
      };
    },

    saveCurrentUser(state, { payload }) {
      setCurrentUser(payload);
      return { ...state, currentUser: payload };
    },

    saveMenuData(state, { payload }) {
      return { ...state, menuData: payload };
    },

    saveCaptcha(state, { payload }) {
      return { ...state, captcha: payload.img, captchaUuid: payload.uuid };
    },
  },

  subscriptions: {
    setup({ dispatch, history }) {
      return history.listen(({ pathname }) => {
        if (pathname !== '/login' && !getToken()) {
          dispatch(routerRedux.replace('/login'));
        }
      });
    },
  },
};
