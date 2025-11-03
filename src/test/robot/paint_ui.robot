*** Settings ***
Library           SwingLibrary
Suite Setup       Start Application    io.github.ozkanpakdil.paint.Main
Suite Teardown    Close Application

*** Variables ***
${FRAME}          name:mainFrame
${CANVAS}         name:drawArea
${LINE_TOOL}      name:T1
${RECT_TOOL}      name:T2
${BUCKET_TOOL}    name:T10
${WHITE_RGB}      -1
${BLACK_RGB}      -16777216

*** Test Cases ***
Draws A Line On The Canvas
    [Documentation]    Select Line tool, drag on the canvas and verify a pixel changed from white.
    Wait Until Window Is Visible    ${FRAME}
    Click On Component    ${LINE_TOOL}
    Mouse Press On Component    ${CANVAS}    20    20
    Mouse Move On Component     ${CANVAS}    120   120
    Mouse Release On Component  ${CANVAS}    120   120
    ${rgb}=    Call Method    ${CANVAS}    getPixelRGB    60    60
    Should Not Be Equal As Integers    ${rgb}    ${WHITE_RGB}

Bucket Fills Inside A Drawn Rectangle
    [Documentation]    Draw rectangle outline, bucket fill inside, then verify inside pixel is black.
    Wait Until Window Is Visible    ${FRAME}
    Click On Component    ${RECT_TOOL}
    Mouse Press On Component    ${CANVAS}    30    30
    Mouse Move On Component     ${CANVAS}    180   140
    Mouse Release On Component  ${CANVAS}    180   140
    Click On Component          ${BUCKET_TOOL}
    Click On Component          ${CANVAS}    40    40
    ${rgb}=    Call Method      ${CANVAS}    getPixelRGB    40    40
    Should Be Equal As Integers    ${rgb}    ${BLACK_RGB}
