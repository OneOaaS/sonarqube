/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import React from 'react';
import debounce from 'lodash/debounce';
import Toggle from '../../../../components/controls/Toggle';
import { defaultInputPropTypes } from '../../propTypes';
import { translate } from '../../../../helpers/l10n';
import { DEBOUNCE_WAIT } from '../../constants';

export default class InputForBoolean extends React.Component {
  static propTypes = {
    ...defaultInputPropTypes,
    value: React.PropTypes.oneOfType([React.PropTypes.bool, React.PropTypes.string])
  };

  constructor (props) {
    super(props);
    this.state = { value: props.value };
    this.handleChange = debounce(this.handleChange.bind(this), DEBOUNCE_WAIT);
  }

  componentWillReceiveProps (nextProps) {
    if (this.props.isDefault !== nextProps.isDefault) {
      this.setState({ value: nextProps.value });
    }
  }

  handleInputChange (value) {
    this.setState({ value });
    this.handleChange(value);
  }

  handleChange (value) {
    this.props.onChange(this.props.setting, value);
  }

  render () {
    const hasValue = this.state.value != null;
    const displayedValue = hasValue ? this.state.value : false;

    return (
        <div className="display-inline-block text-top">
          <Toggle
              name={this.props.name}
              value={displayedValue}
              onChange={value => this.handleInputChange(value)}/>
          {!hasValue && (
              <span className="spacer-left note">{translate('settings.not_set')}</span>
          )}
        </div>
    );
  }
}
