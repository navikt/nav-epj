import React, { PropsWithChildren, ReactElement, ReactNode } from 'react'

import styles from './base-grid.module.css'

type Props = {
    header: ReactNode
    footer: ReactNode
}

function BaseGrid({ footer, header, children }: PropsWithChildren<Props>): ReactElement {
    return (
        <div id={styles.rootGrid}>
            <div id="grid-header">{header}</div>
            {/* Page will provide #grid-sidebar and #grid-content */}
            {children}
            <div id="grid-footer">{footer}</div>
        </div>
    )
}

export default BaseGrid
