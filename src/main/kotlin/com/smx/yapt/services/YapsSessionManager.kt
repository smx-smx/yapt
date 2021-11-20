package com.smx.yapt.services

import com.smx.yapt.ssh.IYapsSession
import com.smx.yapt.ssh.NullYapsSession

class YapsSessionManager {
    var currentSession: IYapsSession = NullYapsSession()
        get set(value) {
            field.close()
            field = value
        }
}